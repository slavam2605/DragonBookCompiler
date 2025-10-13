package compiler.backend.arm64.ops

import compiler.backend.arm64.instructions.CSet
import compiler.backend.arm64.instructions.CmpImm
import compiler.backend.arm64.instructions.ConditionFlag
import compiler.backend.arm64.instructions.Fcvtzs
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.instructions.Scvtf
import compiler.backend.arm64.ops.utils.CopyUtils
import compiler.ir.IRAssign
import compiler.ir.IRConvert
import compiler.ir.IRNot
import compiler.ir.IRType

class SimpleOpsEmitter(private val context: NativeCompilerContext) {
    fun emitAssign(node: IRAssign) {
        context.allocator.writeReg(node.result) { dst ->
            CopyUtils.emitCopy(context, dst, node.right)
        }
    }

    fun emitNot(n: IRNot) {
        context.allocator.readReg(n.value) { v ->
            context.ops.add(CmpImm(v as X, 0))
        }
        context.allocator.writeReg(n.result) { dst ->
            context.ops.add(CSet(dst as X, ConditionFlag.EQ))
        }
    }

    fun emitConvert(node: IRConvert) {
        context.allocator.writeReg(node.result) { dst ->
            context.allocator.readReg(node.value) { src ->
                when {
                    node.result.type == IRType.FLOAT64 && node.value.type == IRType.INT64 -> {
                        check(dst is D && src is X) { "Int to float conversion requires X -> D registers" }
                        context.ops.add(Scvtf(dst, src))
                    }
                    node.result.type == IRType.INT64 && node.value.type == IRType.FLOAT64 -> {
                        check(dst is X && src is D) { "Float to int conversion requires D -> X registers" }
                        context.ops.add(Fcvtzs(dst, src))
                    }
                    else -> error("Invalid conversion: ${node.value.type} -> ${node.result.type}")
                }
            }
        }
    }
}