package compiler.backend

import compiler.ir.*
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.advanceAfterAllVars
import compiler.utils.NameAllocator

/**
 * Pre-backend transformation that inserts explicit copies for function call arguments.
 *
 * This transformation runs AFTER all optimizations but BEFORE native code generation.
 * It ensures that function arguments don't have complex dependencies that would require
 * parallel copy resolution in the backend.
 *
 * Example transformation:
 * ```
 * // Before:
 * call foo(b, a)  // where a and b are variables
 *
 * // After:
 * temp1 = b
 * temp2 = a
 * call foo(temp1, temp2)
 * ```
 *
 * This allows the register allocator to naturally handle parameter shuffling,
 * including cycles like `foo(b, a)` when `a` is in `x0` and `b` is in `x1`.
 */
private class InsertExplicitCopiesForCalls(private val cfg: ControlFlowGraph) {
    private val varAllocator = NameAllocator("x")

    init {
        // Initialize allocator to avoid name conflicts
        varAllocator.advanceAfterAllVars(cfg)
    }

    fun run(): ControlFlowGraph {
        val newBlocks = cfg.blocks.mapValues { (_, block) ->
            CFGBlock(transformBlock(block))
        }
        return ControlFlowGraph(cfg.root, newBlocks)
    }

    private fun transformBlock(block: CFGBlock): List<IRNode> {
        val result = mutableListOf<IRNode>()

        block.irNodes.forEach { node ->
            when (node) {
                is IRFunctionCall -> {
                    // Insert explicit copies for all arguments
                    val copiedArgs = node.arguments.map { arg ->
                        when (arg) {
                            // Constants and simple values don't need copies
                            is IRInt, is IRFloat -> arg

                            // Variables need explicit copies
                            is IRVar -> {
                                val tempVar = IRVar(varAllocator.newName(), arg.type, null)
                                result.add(IRAssign(tempVar, arg))
                                tempVar
                            }
                        }
                    }

                    // Emit call with copied arguments
                    result.add(IRFunctionCall(node.name, node.result, copiedArgs))
                }

                else -> result.add(node)
            }
        }

        return result
    }
}

/**
 * Extension function to make it easy to apply this transformation.
 */
fun ControlFlowGraph.insertExplicitCopiesForCalls(): ControlFlowGraph {
    return InsertExplicitCopiesForCalls(this).run()
}
