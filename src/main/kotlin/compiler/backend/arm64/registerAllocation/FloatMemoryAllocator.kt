package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.Arm64AssemblyCompiler
import compiler.backend.arm64.Instruction
import compiler.backend.arm64.Register.*
import compiler.frontend.FrontendFunction
import compiler.ir.IRType
import compiler.ir.cfg.ControlFlowGraph

class FloatMemoryAllocator(
    compiler: Arm64AssemblyCompiler,
    function: FrontendFunction<ControlFlowGraph>,
    ops: MutableList<Instruction>,
) : BaseMemoryAllocator<D>(compiler, function, ops, IRType.FLOAT64) {
    override val freeTempRegs = FloatTempRegs.toMutableSet()

    override val nonTempRegs = FloatNonTempRegs

    override fun parameterReg(index: Int): D = D(index)

    companion object {
        val FloatTempRegs = D.CalleeSaved.take(3).toSet()
        val FloatNonTempRegs = D.CalleeSaved - FloatTempRegs
    }
}