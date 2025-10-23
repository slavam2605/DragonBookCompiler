package compiler.ir.optimization.clean

import compiler.frontend.FrontendFunctions
import compiler.frontend.isPure
import compiler.ir.IRFunctionCall
import compiler.ir.IRNode
import compiler.ir.IRVar
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
                changed = true

                if (node is IRFunctionCall && !ffs.isPure(node.name)) {
                    // Don't remove a non-pure function call, just remove an unused assignment
                    return IRFunctionCall(node.name, null, node.arguments)
                }

                // For pure function calls and other ir-nodes, just remove unused assignment
                return null
            }
        })
        return if (changed) transformedCfg else cfg
    }
}