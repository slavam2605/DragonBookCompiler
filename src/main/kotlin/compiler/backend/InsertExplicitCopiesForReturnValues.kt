package compiler.backend

import compiler.ir.*
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.advanceAfterAllVars
import compiler.utils.NameAllocator

/**
 * Pre-backend transformation that inserts explicit copies for function call return values.
 *
 * This transformation runs AFTER all optimizations but BEFORE native code generation.
 * It decouples return values from their ABI registers (x0 for int, d0 for float),
 * giving the register allocator more freedom.
 *
 * Example transformation:
 * ```
 * // Before:
 * result = foo(a, b)
 * use(result)
 *
 * // After:
 * temp = foo(a, b)
 * result = temp
 * use(result)
 * ```
 *
 * This allows the register allocator to:
 * - Avoid forced x0/d0 assignments for return values
 * - Better coalesce return values with other variables
 * - Reduce register pressure around function calls
 */
private class InsertExplicitCopiesForReturnValues(private val cfg: ControlFlowGraph) {
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
                    if (node.result != null) {
                        // Create temporary for return value
                        val originalResult = node.result
                        val tempVar = IRVar(varAllocator.newName(), originalResult.type, null)

                        // Call assigns to temp
                        result.add(IRFunctionCall(node.name, tempVar, node.arguments))

                        // Copy temp to original result
                        result.add(IRAssign(originalResult, tempVar))
                    } else {
                        // No return value, keep as is
                        result.add(node)
                    }
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
fun ControlFlowGraph.insertExplicitCopiesForReturnValues(): ControlFlowGraph {
    return InsertExplicitCopiesForReturnValues(this).run()
}
