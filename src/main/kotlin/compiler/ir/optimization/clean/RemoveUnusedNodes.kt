package compiler.ir.optimization.clean

import compiler.frontend.FrontendFunctions
import compiler.frontend.isPure
import compiler.ir.IRFunctionCall
import compiler.ir.IRNode
import compiler.ir.IRType
import compiler.ir.IRVar
import compiler.ir.Intrinsics
import compiler.ir.SimpleIRTransformer
import compiler.ir.cfg.ControlFlowGraph

class RemoveUnusedNodes(
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

        var changed = false
        val transformedCfg = cfg.transform(object : SimpleIRTransformer() {
            override fun transformNodeSimple(node: IRNode): IRNode? {
                val lVar = node.lvalue
                if (lVar == null) {
                    if (node is IRFunctionCall && ffs.isPure(node.name)) {
                        // Remove pure function calls
                        changed = true
                        return null
                    }
                    return node
                }
                if (lVar in usedRVars) return node

                if (node is IRFunctionCall && !ffs.isPure(node.name)) {
                    if (isMalloc(node)) {
                        // TODO remove this hack, store type info in `malloc` node or compile with a cast `malloc(size) as T*`
                        // Special case: don't remove the variable from `malloc`, it contains it's return type information
                        return node
                    }

                    // Don't remove a non-pure function call, just remove an unused assignment
                    changed = true
                    return IRFunctionCall(node.name, null, node.arguments)
                }

                // For pure function calls and other ir-nodes, just remove unused assignment
                changed = true
                return null
            }
        })
        return if (changed) transformedCfg else cfg
    }

    private fun isMalloc(node: IRFunctionCall): Boolean {
        return node.name == Intrinsics.MALLOC &&
                node.arguments.size == 1 &&
                node.arguments[0].type == IRType.INT64
    }
}