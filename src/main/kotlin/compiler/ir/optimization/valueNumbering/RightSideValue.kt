package compiler.ir.optimization.valueNumbering

import compiler.ir.*


@ConsistentCopyVisibility
data class RightSideValue private constructor(private val tag: RSVTag, private val rNumbers: List<Long>) {
    companion object {
        fun fromIRNode(node: IRNode, rNumbers: List<Long>): RightSideValue? {
            val tag = when (node) {
                is IRBinOp -> RSVTag.BinOp(node.op)
                is IRAssign -> RSVTag.Assign
                is IRNot -> RSVTag.Not
                is IRConvert -> RSVTag.Convert(node.result.type)

                is IRJump,          // ignore nodes without a result
                is IRJumpIfTrue,
                is IRReturn,
                is IRPhi,           // ignore nodes with an undefined result
                is IRFunctionCall
                    -> return null
            }

            val numbers = if (tag.isCommutative) rNumbers.sorted() else rNumbers
            return RightSideValue(tag, numbers)
        }
    }
}

private sealed interface RSVTag {
    data class BinOp(val kind: IRBinOpKind) : RSVTag {
        override val isCommutative = kind in commutativeOps
    }

    object Assign : RSVTag
    object Not : RSVTag
    data class Convert(val targetType: IRType) : RSVTag

    val isCommutative: Boolean get() = false
}

private val commutativeOps = setOf(IRBinOpKind.ADD, IRBinOpKind.MUL, IRBinOpKind.EQ, IRBinOpKind.NEQ)