package compiler.backend.arm64.ops.integers

import compiler.backend.arm64.Add
import compiler.backend.arm64.Cmp
import compiler.backend.arm64.IRPeepholeWindow
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.MSub
import compiler.backend.arm64.Mul
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.SDiv
import compiler.backend.arm64.Sub
import compiler.backend.arm64.ops.utils.EmitUtils
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind

class IntegerBinOpEmitter(private val context: NativeCompilerContext) {
    fun emitIntBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        context.allocator.writeReg(node.result) { dst ->
            context.allocator.readReg(node.left) { left ->
                context.allocator.readReg(node.right) { right ->
                    check(dst is X && left is X && right is X)
                    when (node.op) {
                        IRBinOpKind.ADD -> context.ops.add(Add(dst, left, right))
                        IRBinOpKind.SUB -> context.ops.add(Sub(dst, left, right))
                        IRBinOpKind.MUL -> context.ops.add(Mul(dst, left, right))
                        IRBinOpKind.DIV -> context.ops.add(SDiv(dst, left, right))
                        IRBinOpKind.MOD -> {
                            // dst = l - (l / r) * r
                            if (dst == left || dst == right) {
                                context.allocator.tempIntReg { temp ->
                                    context.ops.add(SDiv(temp, left, right))
                                    context.ops.add(MSub(dst, temp, right, left))
                                }
                            } else {
                                context.ops.add(SDiv(dst, left, right))
                                context.ops.add(MSub(dst, dst, right, left))
                            }
                        }
                        IRBinOpKind.EQ, IRBinOpKind.NEQ, IRBinOpKind.GT,
                        IRBinOpKind.GE, IRBinOpKind.LT, IRBinOpKind.LE -> {
                            context.ops.add(Cmp(left, right))
                            EmitUtils.insertComparisonJumpOrSet(context, window, node, node.op, dst)
                        }
                    }
                }
            }
        }
    }
}