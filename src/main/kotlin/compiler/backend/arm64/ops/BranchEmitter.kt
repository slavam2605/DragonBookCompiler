package compiler.backend.arm64.ops

import compiler.backend.arm64.instructions.B
import compiler.backend.arm64.instructions.CmpImm
import compiler.backend.arm64.instructions.ConditionFlag
import compiler.backend.arm64.IntRegister.Companion.D0
import compiler.backend.arm64.IntRegister.Companion.X0
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.ops.utils.CopyUtils
import compiler.backend.arm64.ops.utils.EmitUtils
import compiler.backend.arm64.ops.utils.local
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRReturn
import compiler.ir.IRType

class BranchEmitter(private val context: NativeCompilerContext) {
    fun emitJcc(n: IRJumpIfTrue) {
        context.allocator.readReg(n.cond) { v ->
            context.ops.add(CmpImm(v as X, 0))
        }

        EmitUtils.insertBranches(context, ConditionFlag.NE, n.target, n.elseTarget)
    }

    fun emitB(node: IRJump) {
        if (node.target == context.orderedBlocks.getOrNull(context.currentBlockIndex + 1)) {
            return
        }

        context.ops.add(B(node.target.local(context.function.name)))
    }

    fun emitRet(node: IRReturn) {
        node.value?.let { value ->
            val targetReg = when (value.type) {
                IRType.INT64, is IRType.PTR -> X0
                IRType.FLOAT64 -> D0
            }
            CopyUtils.emitCopy(context, targetReg, value)
        }
        if (context.currentBlockIndex != context.orderedBlocks.lastIndex) {
            context.ops.add(B(context.returnLabel.name))
        }
    }
}