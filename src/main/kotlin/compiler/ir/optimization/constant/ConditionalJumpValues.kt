package compiler.ir.optimization.constant

import compiler.ir.*
import compiler.ir.analysis.DataFlowFramework
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.cfg.ssa.SSAControlFlowGraph

/**
 * Must be called after [compiler.ir.optimization.clean.RemoveUnreachableBlocks], because
 * assertion in `transfer` will fail in some unreachable loops.
 */
class ConditionalJumpValues(private val cfg: SSAControlFlowGraph) {
    fun run(): SSAControlFlowGraph {
        val allTop = cfg.blocks.flatMap { (_, block) ->
            block.irNodes.flatMap { it.rvalues().filterIsInstance<IRVar>() }
        }.toSet().associateWith { SSCPValue.Top as SSCPValue }

        val dfa = DataFlowFramework(
            cfg = cfg,
            direction = DataFlowFramework.Direction.FORWARD,
            identity = allTop,
            meet = { acc, a ->
                (acc.keys + a.keys).associateWith {
                    acc[it]!! * (a[it] ?: SSCPValue.Bottom)
                }
            },
            modifyEdgeValue = ::modifyOutEdge,
            initialOut = { _ -> allTop },
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
        val sourceMap = SourceLocationMap.copyMap(cfg)
        val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
        cfg.blocks.forEach { (label, block) ->
            val condVars = dfa.inValues[label]!!.toMutableMap()
            val transformedBlock = mutableListOf<IRNode>()
            block.irNodes.forEach { node ->
                val newNode = mergePhiSources(node, label, dfa)
                if (node !== newNode) cfgChanged = true

                val transformedNode = newNode.transform(object : BaseIRTransformer() {
                    override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue {
                        if (value !is IRVar) return value
                        (condVars[value] as? SSCPValue.IntValue)?.let { sscpValue ->
                            cfgChanged = true
                            return IRInt(sscpValue.value)
                        }

                        // Update phi-node sources along the edge
                        if (node !is IRPhi) return value
                        val from = node.sources[index].from
                        val edgeOut = modifyOutEdge(from, label, dfa.outValues[from]!!)
                        (edgeOut[value] as? SSCPValue.IntValue)?.let { sscpValue ->
                            cfgChanged = true
                            return IRInt(sscpValue.value)
                        }

                        return value
                    }
                })
                transformedBlock.add(transformedNode)
                node.lvalue?.let {
                    condVars.remove(it)
                }
                sourceMap.replace(node, transformedNode)
            }
            newBlocks[label] = CFGBlock(transformedBlock)
        }
        if (!cfgChanged) return cfg
        return SSAControlFlowGraph(cfg.root, newBlocks).also {
            SourceLocationMap.storeMap(sourceMap, it)
        }
    }

    /**
     * Tries to replace a phi-node with a single assignment by finding a variable
     * that has the same value as the source values of the phi-node.
     *
     * Example:
     * ```
     *  L0: jump-if-true x L1 else L2
     *  L1: y = phi(L3: x, L0: 1)
     * ```
     * In this case, value from `L3` is `x`, and value from `L0` is `1`.
     * But `x == 1` along the edge `L0 -> L1`, so `y = phi(L3: x, L0: x)` => `y = x`.
     */
    private fun mergePhiSources(node: IRNode, label: IRLabel, dfa: DataFlowFramework<Map<IRVar, SSCPValue>>): IRNode {
        if (node !is IRPhi) return node
        val sourceVars = node.sources.mapNotNull { it.value as? IRVar }.distinct()
        if (sourceVars.size != 1) return node

        val irVar = sourceVars.single()
        node.sources.forEach { source ->
            if (source.value == irVar) {
                return@forEach // success, check next source
            }

            val thisValue = (source.value as? IRInt)?.value
                ?: return node // failure, source value must be IRInt or `irVar`
                               // floats are not supported, for now edge values are only integers (booleans)

            val edgeValues = modifyOutEdge(source.from, label, dfa.outValues[source.from]!!)
            val fromValue = (edgeValues[irVar] as? SSCPValue.IntValue)?.value
            if (thisValue != fromValue) {
                return node // failure, constant value doesn't match
            }
        }
        return IRAssign(node.lvalue, irVar)
    }

    private fun modifyOutEdge(from: IRLabel, to: IRLabel, outMap: Map<IRVar, SSCPValue>): Map<IRVar, SSCPValue> {
        val jump = cfg.blocks[from]!!.irNodes.lastOrNull() as? IRJumpIfTrue
        if (jump == null || jump.cond !is IRVar || jump.target == jump.elseTarget) {
            return outMap
        }

        return outMap.toMutableMap().also { newMap ->
            check(jump.target != jump.elseTarget)
            if (to == jump.target) newMap[jump.cond] = SSCPValue.IntValue(1L)
            if (to == jump.elseTarget) newMap[jump.cond] = SSCPValue.IntValue(0L)
        }
    }
}