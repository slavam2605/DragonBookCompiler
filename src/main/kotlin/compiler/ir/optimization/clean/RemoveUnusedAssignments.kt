package compiler.ir.optimization.clean

import compiler.frontend.FrontendFunctions
import compiler.frontend.isPure
import compiler.ir.IRFunctionCall
import compiler.ir.IRNode
import compiler.ir.IRVar
import compiler.ir.SimpleIRTransformer
import compiler.ir.cfg.ControlFlowGraph

class RemoveUnusedAssignments(
    private val cfg: ControlFlowGraph,
    private val ffs: FrontendFunctions<out ControlFlowGraph>
) {
    fun invoke(): ControlFlowGraph {
        // Collect all used variables
        val usedRVars = mutableSetOf<IRVar>()
        val usedLVars = mutableSetOf<IRVar>()
        cfg.blocks.values.forEach { block ->
            block.irNodes.forEach { irNode ->
                irNode.lvalue?.let { usedLVars.add(it) }
                usedRVars.addAll(irNode.rvalues().filterIsInstance<IRVar>())
            }
        }

        if (usedLVars.all { it in usedRVars }) {
            // All variables are used, return the original CFG
            return cfg
        }

        return cfg.transform(object : SimpleIRTransformer() {
            override fun transformNodeSimple(node: IRNode): IRNode? {
                val lVar = node.lvalue ?: return node
                if (lVar in usedRVars) return node
                if (node is IRFunctionCall && !ffs.isPure(node.name)) {
                    // Don't remove a non-pure function call, just remove an unused assignment
                    return IRFunctionCall(node.name, null, node.arguments)
                }

                // For pure function calls and other ir-nodes, just remove unused assignment
                return null
            }
        })
    }
}