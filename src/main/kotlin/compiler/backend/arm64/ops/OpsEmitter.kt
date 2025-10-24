package compiler.backend.arm64.ops

import compiler.backend.arm64.IRPeepholeWindow
import compiler.backend.arm64.NativeCompilerContext
import compiler.ir.*

class OpsEmitter(context: NativeCompilerContext) {
    private val binOps = BinOpEmitter(context)
    private val simpleOps = SimpleOpsEmitter(context)
    private val callOps = CallEmitter(context)
    private val branchOps = BranchEmitter(context)

    fun emitOnce(window: IRPeepholeWindow) {
        val node = window.current ?: return
        window.move()
        when (node) {
            is IRBinOp -> binOps.emitBinOp(node, window)
            is IRAssign -> simpleOps.emitAssign(node)
            is IRNot -> simpleOps.emitNot(node)
            is IRConvert -> simpleOps.emitConvert(node)
            is IRFunctionCall -> callOps.emitCall(node)
            is IRLoad -> simpleOps.emitLoad(node)
            is IRStore -> simpleOps.emitStore(node)
            is IRJumpIfTrue -> branchOps.emitJcc(node)
            is IRJump -> branchOps.emitB(node)
            is IRReturn -> branchOps.emitRet(node)
            is IRPhi -> error("Backend compilation supports only non-SSA IR, phi-nodes are not allowed")
        }
    }
}