package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.Arm64AssemblyCompiler
import compiler.backend.arm64.Instruction
import compiler.backend.arm64.IntRegister.X
import compiler.frontend.FrontendFunction
import compiler.ir.IRType
import compiler.ir.cfg.ControlFlowGraph

class IntMemoryAllocator(
    compiler: Arm64AssemblyCompiler,
    function: FrontendFunction<ControlFlowGraph>,
    ops: MutableList<Instruction>,
) : BaseMemoryAllocator<X>(compiler, function, ops, IRType.INT64) {
    override val freeTempRegs = IntTempRegs.toMutableSet()

    override val nonTempRegs = IntNonTempRegs

    override fun parameterReg(index: Int): X = X(index)

    companion object {
        val IntTempRegs = X.CallerSaved.take(3).toSet()
        val IntNonTempRegs = X.CalleeSaved - IntTempRegs
    }
}