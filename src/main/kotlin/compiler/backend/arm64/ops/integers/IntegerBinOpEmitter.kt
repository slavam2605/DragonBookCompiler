package compiler.backend.arm64.ops.integers

import compiler.backend.arm64.instructions.Add
import compiler.backend.arm64.instructions.Cmp
import compiler.backend.arm64.IRPeepholeWindow
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.instructions.MSub
import compiler.backend.arm64.instructions.Mul
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.instructions.SDiv
import compiler.backend.arm64.instructions.Sub
import compiler.backend.arm64.ops.utils.EmitUtils
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRInt

class IntegerBinOpEmitter(private val context: NativeCompilerContext) {
    fun emitIntBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        context.allocator.writeReg(node.result) { dst ->
            context.allocator.readReg(node.left) { left ->
                check(dst is X && left is X)
                when (node.op) {
                    IRBinOpKind.ADD -> context.allocator.readReg(node.right) { right ->
                        context.ops.add(Add(dst, left, right as X))
                    }
                    IRBinOpKind.SUB -> context.allocator.readReg(node.right) { right ->
                        context.ops.add(Sub(dst, left, right as X))
                    }
                    IRBinOpKind.MUL -> {
                        if (node.right is IRInt) {
                            IntConstantMultiplication.emitMultiply(context, dst, left, node.right.value)
                            return@readReg
                        }
                        context.allocator.readReg(node.right) { right ->
                            context.ops.add(Mul(dst, left, right as X))
                        }
                    }
                    IRBinOpKind.DIV -> {
                        if (node.right is IRInt) {
                            val success = IntConstantDivision.tryEmitConstantDivision(context, dst, left,
                                node.right.value, false)
                            if (success) return@readReg
                        }
                        context.allocator.readReg(node.right) { right ->
                            context.ops.add(SDiv(dst, left, right as X))
                        }
                    }
                    IRBinOpKind.MOD -> {
                        if (node.right is IRInt) {
                            val success = IntConstantDivision.tryEmitConstantDivision(context, dst, left,
                                node.right.value, true)
                            if (success) return@readReg
                        }
                        context.allocator.readReg(node.right) { right -> right as X
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
                    }
                    IRBinOpKind.EQ, IRBinOpKind.NEQ, IRBinOpKind.GT,
                    IRBinOpKind.GE, IRBinOpKind.LT, IRBinOpKind.LE -> {
                        context.allocator.readReg(node.right) { right ->
                            context.ops.add(Cmp(left, right as X))
                            EmitUtils.insertComparisonJumpOrSet(context, window, node, node.op, dst)
                        }
                    }
                }
            }
        }
    }
}