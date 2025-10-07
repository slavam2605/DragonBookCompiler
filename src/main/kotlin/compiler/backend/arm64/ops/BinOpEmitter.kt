package compiler.backend.arm64.ops

import compiler.backend.arm64.*
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.ops.utils.EmitUtils
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRType

class BinOpEmitter(private val context: NativeCompilerContext) {
    fun emitBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        require(node.left.type == node.right.type) {
            "IRBinOp requires operands of the same type, got ${node.left.type} and ${node.right.type}"
        }
        if (node.left.type == IRType.FLOAT64) {
            emitFloatBinOp(node, window)
        } else {
            emitIntBinOp(node, window)
        }
    }

    private fun emitIntBinOp(node: IRBinOp, window: IRPeepholeWindow) {
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
                            context.ops.add(SDiv(dst, left, right))
                            context.ops.add(MSub(dst, dst, right, left))
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

    private fun emitFloatBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        context.allocator.writeReg(node.result) { dst ->
            context.allocator.readReg(node.left) { left -> left as D
                context.allocator.readReg(node.right) { right -> right as D
                    when (node.op) {
                        IRBinOpKind.ADD -> context.ops.add(FAdd(dst as D, left, right))
                        IRBinOpKind.SUB -> context.ops.add(FSub(dst as D, left, right))
                        IRBinOpKind.MUL -> context.ops.add(FMul(dst as D, left, right))
                        IRBinOpKind.DIV -> context.ops.add(FDiv(dst as D, left, right))
                        IRBinOpKind.MOD -> {
                            error("Float modulo is not supported")
                        }

                        IRBinOpKind.EQ, IRBinOpKind.NEQ, IRBinOpKind.GT,
                        IRBinOpKind.GE, IRBinOpKind.LT, IRBinOpKind.LE -> {
                            context.ops.add(FCmp(left, right))
                            EmitUtils.insertComparisonJumpOrSet(context, window, node, node.op, dst as X)
                        }
                    }
                }
            }
        }
    }
}