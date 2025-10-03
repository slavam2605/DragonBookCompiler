package compiler.backend

import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey

/**
 * Prepares a control flow graph for native code generation by applying
 * architecture-independent transformations.
 *
 * This function should be called AFTER all optimizations but BEFORE any
 * architecture-specific code generation.
 *
 * Current transformations:
 * - Insert explicit copies for function call arguments to avoid parameter shuffling issues
 */
object PrepareForNativeCompilation {
    private val key = ExtensionKey<Boolean>("isPreparedForNativeCompilation")

    fun run(cfg: ControlFlowGraph): ControlFlowGraph {
        var result = cfg

        // Insert explicit copies for function call arguments
        // This prevents register allocation issues when parameters need to be shuffled
        // (e.g., calling foo(b, a) when a is in x0 and b is in x1)
        result = result.insertExplicitCopiesForCalls()

        result.putExtension(key, true)
        return result
    }

    fun isPrepared(cfg: ControlFlowGraph): Boolean = cfg.getExtension(key) ?: false
}