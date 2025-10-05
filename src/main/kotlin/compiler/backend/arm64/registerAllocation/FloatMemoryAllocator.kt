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
    analysisResult: AllocationAnalysisResult
) : BaseMemoryAllocator<D>(compiler, function, ops, IRType.FLOAT64, analysisResult) {
    override fun callerSaved() = D.CallerSaved

    override fun calleeSaved() = D.CalleeSaved

    override fun parameterRegs() = (0..7).map { D(it) }
}