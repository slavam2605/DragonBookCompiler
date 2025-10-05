package compiler.backend

import compiler.ir.*
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.utils.NameAllocator

/**
 * Pre-backend transformation that inserts explicit copies for function parameters.
 *
 * This transformation runs AFTER all optimizations but BEFORE native code generation.
 * It decouples parameters from their ABI registers (x0-x7, d0-d7), giving the register
 * allocator more freedom to allocate them optimally.
 *
 * Example transformation:
 * ```
 * // Before:
 * fun foo(int a, int b) {
 *     x1 = a + b
 * }
 *
 * // After:
 * fun foo(int a, int b) {
 *     t1 = a
 *     t2 = b
 *     x1 = t1 + t2
 * }
 * ```
 *
 * This allows the register allocator to:
 * - Allocate long-lived parameters to callee-saved registers
 * - Avoid forced parameter register assignments
 * - Better coalesce with other variables
 */
private class InsertExplicitCopiesForParameters(
    private val cfg: ControlFlowGraph,
    private val parameters: List<IRVar>
) {
    private val varAllocator = NameAllocator("x")
    private val replacementMap = mutableMapOf<IRVar, IRVar>()

    init {
        // Initialize allocator to avoid name conflicts
        cfg.blocks.values.forEach { block ->
            block.irNodes.forEach { node ->
                node.lvalue?.let { varAllocator.advanceAfter(it.name) }
            }
        }

        // Create temporary variables for each parameter
        parameters.forEach { param ->
            val temp = IRVar(varAllocator.newName(), param.type, param.sourceName)
            replacementMap[param] = temp
        }
    }

    fun run(): ControlFlowGraph {
        // Transform blocks
        val newBlocks = cfg.blocks.mapValues { (label, block) ->
            if (label == cfg.root) {
                // Insert parameter copies at the start of root block
                val paramCopies = parameters.map { param ->
                    IRAssign(replacementMap[param]!!, param)
                }
                CFGBlock(paramCopies + transformBlock(block))
            } else {
                CFGBlock(transformBlock(block))
            }
        }

        return ControlFlowGraph(cfg.root, newBlocks)
    }

    private fun transformBlock(block: CFGBlock): List<IRNode> {
        // Use BaseIRTransformer to replace parameter uses
        val transformer = object : BaseIRTransformer() {
            override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue {
                return if (value is IRVar && value in replacementMap) {
                    replacementMap[value]!!
                } else {
                    value
                }
            }
        }

        return block.irNodes.map { it.transform(transformer) }
    }
}

/**
 * Extension function to make it easy to apply this transformation.
 */
fun ControlFlowGraph.insertExplicitCopiesForParameters(parameters: List<IRVar>): ControlFlowGraph {
    if (parameters.isEmpty()) return this
    return InsertExplicitCopiesForParameters(this, parameters).run()
}
