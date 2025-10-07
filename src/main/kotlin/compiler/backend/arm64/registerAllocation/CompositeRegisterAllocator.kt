package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.*
import compiler.frontend.FrontendFunction
import compiler.ir.IRType
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

/**
 * Composite register allocator that manages both integer and floating-point register allocation.
 * Creates a single type-agnostic interference graph and liveness info, then delegates to
 * type-specific allocators.
 */
class CompositeRegisterAllocator(
    context: NativeCompilerContext,
    function: FrontendFunction<ControlFlowGraph>,
    ops: MutableList<Instruction>
) : MemoryAllocator<Register> {
    private val intAllocator: IntMemoryAllocator
    private val floatAllocator: FloatMemoryAllocator

    override val alignedAllocatedSize: Int
        get() {
            // Float allocator is used here because it stores max of int and float stack offsets
            // TODO tidy up this place, find explicit maximum or share this info somehow
            return floatAllocator.alignedAllocatedSize
        }

    init {
        // Create a type-agnostic interference graph and liveness info once
        val result = InterferenceGraph.create(function.value)

        // Initialize allocators with shared graph and liveness info
        intAllocator = IntMemoryAllocator(context, function, ops, result)
        intAllocator.init(minStackOffset = 0)

        floatAllocator = FloatMemoryAllocator(context, function, ops, result)
        // Do not allocate the same stack locations for int and float variables
        floatAllocator.init(minStackOffset = intAllocator.nextStackOffset)
    }

    override fun <T : Register> usedRegisters(clazz: Class<T>?): Set<T> {
        return intAllocator.usedRegisters(clazz) + floatAllocator.usedRegisters(clazz)
    }

    override fun loc(v: IRVar): MemoryLocation {
        return chooseAllocator(v).loc(v)
    }

    override fun <T> writeReg(v: IRVar, block: (Register) -> T): T {
        return chooseAllocator(v).writeReg(v, block)
    }

    @Suppress("UNCHECKED_CAST")
    override fun readReg(v: IRValue): RegHandle<Register> {
        return chooseAllocator(v).readReg(v) as RegHandle<Register>
    }

    fun <T> tempReg(typeHolder: IRValue, block: (Register) -> T): T {
        return chooseAllocator(typeHolder).tempReg(block)
    }

    fun <T> tempIntReg(block: (IntRegister.X) -> T): T {
        return intAllocator.tempReg(block)
    }

    fun <T> tempFloatReg(block: (Register.D) -> T): T {
        return floatAllocator.tempReg(block)
    }

    /**
     * Returns liveness information shared by both allocators.
     * The liveness analysis is performed once during initialization and shared
     * between int and float allocators via AllocationAnalysisResult.
     */
    fun getLivenessInfo() = intAllocator.livenessInfo

    private fun chooseAllocator(value: IRValue): BaseMemoryAllocator<*> = when (value.type) {
        IRType.INT64 -> intAllocator
        IRType.FLOAT64 -> floatAllocator
    }
}