package compiler.ir.optimization.constant

import compiler.ir.*
import compiler.ir.cfg.ssa.SSAControlFlowGraph

@Deprecated("Use SparseConditionalConstantPropagation instead")
class ConstantPropagation {
    val values = mutableMapOf<IRVar, SSCPValue>()

    fun run(cfg: SSAControlFlowGraph): SSAControlFlowGraph {
        val worklist = mutableListOf<IRVar>()
        val usages = mutableMapOf<IRVar, MutableSet<IRNode>>()

        cfg.blocks.forEach { (_, block) ->
            block.irNodes.forEach { irNode ->
                // Initialize all known constant values and form the initial worklist
                irNode.lvalue?.let { lVar ->
                    val value = irNode.evaluateSafe(values)
                    if (value !is SSCPValue.Top) {
                        values[lVar] = value
                        worklist.add(lVar)
                    }
                }

                // Fill in the usages map
                irNode.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                    val rVarUsages = usages.getOrPut(rVar) { mutableSetOf() }
                    rVarUsages.add(irNode)
                }
            }
        }

        while (worklist.isNotEmpty()) {
            val irVar = worklist.removeLast()
            usages[irVar]?.forEach { irNode ->
                irNode.lvalue?.let { nodeResult ->
                    if (values[nodeResult] is SSCPValue.Bottom) {
                        // Can't get further in lattice than the bottom,
                        // it doesn't make sense recomputing this value
                        return@let
                    }

                    val oldValue = values[nodeResult] ?: SSCPValue.Top
                    val newValue = irNode.evaluateSafe(values)
                    if (oldValue != newValue) {
                        check(newValue == oldValue * newValue)
                        values[nodeResult] = newValue
                        worklist.add(nodeResult)
                    }
                }
            }
        }

        return FoldConstantExpressions.run(cfg, values)
    }
}