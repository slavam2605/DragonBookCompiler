package compiler.backend

import compiler.ir.IRVar
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
 * - Insert explicit copies for function parameters to decouple from ABI registers
 * - Insert explicit copies for function call arguments to avoid parameter shuffling issues
 * - Insert explicit copies for function return values to decouple from x0/d0
 */
object PrepareForNativeCompilation {
    private val key = ExtensionKey<Boolean>("isPreparedForNativeCompilation")

    fun run(cfg: ControlFlowGraph, parameters: List<IRVar>): ControlFlowGraph {
        var result = cfg

        // Insert explicit copies for function parameters
        // This decouples parameters from ABI registers (x0-x7, d0-d7),
        // allowing the register allocator to allocate them more freely
        result = result.insertExplicitCopiesForParameters(parameters)

        // Insert explicit copies for function call arguments
        // This prevents register allocation issues when parameters need to be shuffled
        // (e.g., calling foo(b, a) when a is in x0 and b is in x1)
        result = result.insertExplicitCopiesForCalls()

        // Insert explicit copies for function return values
        // This decouples return values from x0/d0, allowing better register allocation
        result = result.insertExplicitCopiesForReturnValues()

        result.putExtension(key, true)
        return result
    }

    fun isPrepared(cfg: ControlFlowGraph): Boolean = cfg.getExtension(key) ?: false
}