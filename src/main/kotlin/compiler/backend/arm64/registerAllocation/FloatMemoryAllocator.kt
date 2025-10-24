package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.instructions.Instruction
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register.D
import compiler.frontend.FrontendFunction
import compiler.ir.cfg.ControlFlowGraph

class FloatMemoryAllocator(
    context: NativeCompilerContext,
    function: FrontendFunction<ControlFlowGraph>,
    ops: MutableList<Instruction>,
    analysisResult: AllocationAnalysisResult
) : BaseMemoryAllocator<D>(context, function, ops, Arm64StorageType.FLOAT_REG, analysisResult) {
    override fun callerSaved() = D.CallerSaved

    override fun calleeSaved() = D.CalleeSaved

    override fun parameterRegs() = (0..7).map { D(it) }
}