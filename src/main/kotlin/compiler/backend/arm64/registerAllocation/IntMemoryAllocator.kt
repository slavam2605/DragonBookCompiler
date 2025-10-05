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
    override fun callerSaved() = X.CallerSaved

    override fun calleeSaved() = X.CalleeSaved

    override fun parameterRegs() = (0..7).map { X(it) }
}