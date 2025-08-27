package compiler.ir.optimization.constant

import compiler.ir.*
import compiler.ir.analysis.DataFlowFramework
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ssa.SSAControlFlowGraph

/**
 * Must be called after [compiler.ir.optimization.clean.RemoveUnreachableBlocks], because
 * assertion in `transfer` will fail in some unreachable loops.
 */
class ConditionalJumpValues(private val cfg: SSAControlFlowGraph) {
    fun run(): SSAControlFlowGraph {
        val dfa = DataFlowFramework(
            cfg = cfg,
            direction = DataFlowFramework.Direction.FORWARD,
            identity = emptyMap(),
            meet = { acc, a ->
                (acc.keys + a.keys).associateWith {
                    (acc[it] ?: SSCPValue.Top) * (a[it] ?: SSCPValue.Bottom)
                }
            },
            modifyEdgeValue = ::modifyOutEdge,
            initialOut = { _ -> emptyMap() },
            transfer = { label, inMap ->
                val outMap = inMap.toMutableMap()
                cfg.blocks[label]!!.irNodes.forEach { node ->
                    node.lvalue?.let {
                        outMap.remove(it)
                    }
                }
                outMap
            }
        ).run()

        var cfgChanged = false
        val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
        cfg.blocks.forEach { (label, block) ->
            val condVars = dfa.inValues[label]!!.toMutableMap()
            val transformedBlock = mutableListOf<IRNode>()
            block.irNodes.forEach { node ->
                transformedBlock.add(node.transform(object : BaseIRTransformer() {
                    override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue {
                        if (value !is IRVar) return value
                        (condVars[value] as? SSCPValue.Value)?.let { sscpValue ->
                            cfgChanged = true
                            return IRInt(sscpValue.value)
                        }

                        // Update phi-node sources along the edge
                        if (node !is IRPhi) return value
                        val from = node.sources[index].from
                        val edgeOut = modifyOutEdge(from, label, dfa.outValues[from]!!)
                        (edgeOut[value] as? SSCPValue.Value)?.let { sscpValue ->
                            cfgChanged = true
                            return IRInt(sscpValue.value)
                        }

                        return value
                    }
                }))
                node.lvalue?.let {
                    condVars.remove(it)
                }
            }
            newBlocks[label] = CFGBlock(transformedBlock)
        }
        if (!cfgChanged) return cfg
        return SSAControlFlowGraph(cfg.root, newBlocks)
    }

    private fun modifyOutEdge(from: IRLabel, to: IRLabel, outMap: Map<IRVar, SSCPValue>): Map<IRVar, SSCPValue> {
        val jump = cfg.blocks[from]!!.irNodes.lastOrNull() as? IRJumpIfTrue
        if (jump == null || jump.cond !is IRVar || jump.target == jump.elseTarget) {
            return outMap
        }

        return outMap.toMutableMap().also { newMap ->
            check(jump.target != jump.elseTarget)
            if (to == jump.target) newMap[jump.cond] = SSCPValue.Value(1L)
            if (to == jump.elseTarget) newMap[jump.cond] = SSCPValue.Value(0L)
        }
    }
}