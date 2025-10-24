package compiler.backend.arm64.ops

import compiler.backend.arm64.IRPeepholeWindow
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.ops.floats.FloatBinOpEmitter
import compiler.backend.arm64.ops.integers.IntegerBinOpEmitter
import compiler.ir.IRBinOp
import compiler.ir.IRType

class BinOpEmitter(context: NativeCompilerContext) {
    private val intEmitter = IntegerBinOpEmitter(context)
    private val floatEmitter = FloatBinOpEmitter(context)

    fun emitBinOp(node: IRBinOp, window: IRPeepholeWindow) {
        require(node.left.type == node.right.type || node.left.type is IRType.PTR && node.right.type == IRType.INT64) {
            "IRBinOp requires operands of the same type or a pointer and i64, got ${node.left.type} and ${node.right.type}"
        }
        if (node.left.type == IRType.FLOAT64) {
            floatEmitter.emitFloatBinOp(node, window)
        } else {
            check(node.left.type == IRType.INT64 || node.left.type is IRType.PTR)
            intEmitter.emitIntBinOp(node, window)
        }
    }
}