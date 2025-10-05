package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.Arm64AssemblyCompiler
import compiler.backend.arm64.Instruction
import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.MemoryLocation
import compiler.backend.arm64.Register
import compiler.frontend.FrontendFunction
import compiler.ir.IRType
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class AggregatedRegisterAllocator(
    compiler: Arm64AssemblyCompiler,
    function: FrontendFunction<ControlFlowGraph>,
    ops: MutableList<Instruction>
) : MemoryAllocator<Register> {
    private val intAllocator = IntMemoryAllocator(compiler, function, ops)
    private val floatAllocator = FloatMemoryAllocator(compiler, function, ops)

    override val alignedAllocatedSize: Int
        get() {
            // Float allocator is used here because it stores max of int and float stack offsets
            // TODO tidy up this place, find explicit maximum or share this info somehow
            return floatAllocator.alignedAllocatedSize
        }

    init {
        intAllocator.init(minStackOffset = 0)
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
     * Returns combined liveness-at-calls information from both int and float allocators.
     * Note: Both allocators analyze the same CFG, so they produce identical liveAtCalls maps.
     * We use the int allocator's data as the canonical source.
     *
     * instead of running it separately for int and float allocators.
     */
    fun getLiveAtCalls() = intAllocator.interferenceGraph.liveAtCalls

    private fun chooseAllocator(value: IRValue): BaseMemoryAllocator<*> = when (value.type) {
        IRType.INT64 -> intAllocator
        IRType.FLOAT64 -> floatAllocator
    }
}