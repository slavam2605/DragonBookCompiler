package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.instructions.Instruction
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.NativeCompilerContext
import compiler.frontend.FrontendFunction
import compiler.ir.cfg.ControlFlowGraph

class IntMemoryAllocator(
    context: NativeCompilerContext,
    function: FrontendFunction<ControlFlowGraph>,
    ops: MutableList<Instruction>,
    analysisResult: AllocationAnalysisResult
) : BaseMemoryAllocator<X>(context, function, ops, Arm64StorageType.INT_REG, analysisResult) {
    override fun callerSaved() = X.CallerSaved

    override fun calleeSaved() = X.CalleeSaved

    override fun parameterRegs() = (0..7).map { X(it) }
}