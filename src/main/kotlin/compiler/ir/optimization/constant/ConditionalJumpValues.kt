package compiler.ir.optimization.constant

import compiler.ir.BaseIRTransformer
import compiler.ir.IRInt
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.analysis.DataFlowFramework
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Must be called after [compiler.ir.optimization.clean.RemoveUnreachableBlocks], because
 * assertion in `transfer` will fail in some unreachable loops.
 */
class ConditionalJumpValues(private val cfg: SSAControlFlowGraph) {
    fun run(): SSAControlFlowGraph {
        val dfa = DataFlowFramework<Map<IRVar, SSCPValue>>(
            cfg = cfg,
            bottom = emptyMap(),
            meet = { a, b ->
                (a.keys + b.keys).associateWith {
                    (a[it] ?: SSCPValue.Bottom) * (b[it] ?: SSCPValue.Bottom)
                }
            },
            modifyOutEdge = ::modifyOutEdge,
            transfer = { label, inMap ->
                cfg.blocks[label]!!.irNodes.forEach { node ->
                    node.lvalue?.let {
                        // If this check is false, then `transformRValue` below may be incorrect
                        check(it !in inMap || inMap[it] is SSCPValue.Bottom)
                    }
                }
                inMap
            }
        )
        dfa.run()

        var cfgChanged = false
        val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
        cfg.blocks.forEach { (label, block) ->
            val condVars = dfa.inValues[label]!!
            newBlocks[label] = block.transform(object : BaseIRTransformer() {
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
            })
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