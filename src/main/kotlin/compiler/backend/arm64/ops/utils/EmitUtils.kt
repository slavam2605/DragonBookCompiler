package compiler.backend.arm64.ops.utils

import compiler.backend.arm64.*
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.instructions.B
import compiler.backend.arm64.instructions.BCond
import compiler.backend.arm64.instructions.CSet
import compiler.backend.arm64.instructions.ConditionFlag
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRLabel

object EmitUtils {
    fun insertComparisonJumpOrSet(context: NativeCompilerContext, window: IRPeepholeWindow,
                                  node: IRBinOp, op: IRBinOpKind, dst: X) {
        val nextIr = window.current as? IRJumpIfTrue
        if (nextIr?.cond == node.result && !context.allocator.getLivenessInfo().isLiveAfterBranch(node.result, nextIr)) {
            window.move()
            insertBranches(context, op.toConditionFlag(), nextIr.target, nextIr.elseTarget)
        } else {
            context.ops.add(CSet(dst, op.toConditionFlag()))
        }
    }

    fun insertBranches(context: NativeCompilerContext, flag: ConditionFlag, trueTarget: IRLabel, falseTarget: IRLabel) {
        val nextLabel = context.orderedBlocks.getOrNull(context.currentBlockIndex + 1)?.local() ?: context.returnLabel.name
        val trueLabel = trueTarget.local()
        val falseLabel = falseTarget.local()

        when (nextLabel) {
            trueLabel -> {
                context.ops.add(BCond(flag.invert(), falseLabel))
            }
            falseLabel -> {
                context.ops.add(BCond(flag, trueLabel))
            }
            else -> {
                context.ops.add(BCond(flag, trueLabel))
                context.ops.add(B(falseLabel))
            }
        }
    }
}

fun IRBinOpKind.toConditionFlag(): ConditionFlag {
    return when (this) {
        IRBinOpKind.EQ -> ConditionFlag.EQ
        IRBinOpKind.NEQ -> ConditionFlag.NE
        IRBinOpKind.GT -> ConditionFlag.GT
        IRBinOpKind.GE -> ConditionFlag.GE
        IRBinOpKind.LT -> ConditionFlag.LT
        IRBinOpKind.LE -> ConditionFlag.LE
        else -> error("Unexpected comparison operator: $this")
    }
}

fun IRLabel.local() = ".$name"