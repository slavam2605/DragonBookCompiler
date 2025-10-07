package compiler.backend.arm64.ops.floats

import compiler.backend.arm64.FAdd
import compiler.backend.arm64.FCmp
import compiler.backend.arm64.FDiv
import compiler.backend.arm64.FMul
import compiler.backend.arm64.FSub
import compiler.backend.arm64.IRPeepholeWindow
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.ops.utils.EmitUtils
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRFloat

class FloatBinOpEmitter(private val context: NativeCompilerContext) {
    fun emitFloatBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        context.allocator.writeReg(node.result) { dst ->
            context.allocator.readReg(node.left) { left -> left as D

                when (node.op) {
                    IRBinOpKind.ADD -> context.allocator.readReg(node.right) { right ->
                        context.ops.add(FAdd(dst as D, left, right as D))
                    }
                    IRBinOpKind.SUB -> context.allocator.readReg(node.right) { right ->
                        context.ops.add(FSub(dst as D, left, right as D))
                    }
                    IRBinOpKind.MUL -> context.allocator.readReg(node.right) { right ->
                        context.ops.add(FMul(dst as D, left, right as D))
                    }
                    IRBinOpKind.DIV -> {
                        if (node.right is IRFloat) {
                            val success = FloatConstantDivision.tryEmitConstantDivision(
                                context, dst as D, left, node.right.value
                            )
                            if (success) return@readReg
                        }
                        context.allocator.readReg(node.right) { right ->
                            context.ops.add(FDiv(dst as D, left, right as D))
                        }
                    }
                    IRBinOpKind.MOD -> {
                        error("Float modulo is not supported")
                    }

                    IRBinOpKind.EQ, IRBinOpKind.NEQ, IRBinOpKind.GT,
                    IRBinOpKind.GE, IRBinOpKind.LT, IRBinOpKind.LE -> {
                        context.allocator.readReg(node.right) { right ->
                            context.ops.add(FCmp(left, right as D))
                            EmitUtils.insertComparisonJumpOrSet(context, window, node, node.op, dst as X)
                        }
                    }
                }
            }
        }
    }
}