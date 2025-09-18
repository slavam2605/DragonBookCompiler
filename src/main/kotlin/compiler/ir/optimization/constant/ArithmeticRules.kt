package compiler.ir.optimization.constant

import compiler.ir.IRAssign
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRFloat
import compiler.ir.IRInt
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue

object ArithmeticRules {
    private fun IRValue.asInt() = (this as? IRInt)?.value

    private fun IRValue.asFloat() = (this as? IRFloat)?.value

    private fun IRValue.isZero() = asInt() == 0L || asFloat() == 0.0

    private fun IRValue.isOne() = asInt() == 1L || asFloat() == 1.0

    private fun IRNode.toAssign(value: IRValue) = IRAssign(lvalue!!, value)

    fun simplifyNode(node: IRNode): IRNode? {
        return when (node) {
            is IRBinOp -> when (node.op) {
                IRBinOpKind.ADD -> when {
                    node.left.isZero() -> node.toAssign(node.right)
                    node.right.isZero() -> node.toAssign(node.left)
                    else -> null
                }
                IRBinOpKind.SUB -> when {
                    node.right.isZero() -> node.toAssign(node.left)
                    node.left == node.right -> node.toAssign(IRInt(0))
                    else -> null
                }
                IRBinOpKind.MUL -> when {
                    node.left.isZero() || node.right.isZero() -> node.toAssign(IRInt(0))
                    node.left.isOne() -> node.toAssign(node.right)
                    node.right.isOne() -> node.toAssign(node.left)
                    // TODO maybe should be replaced by strength reduction
                    node.left.asInt() == 2L -> IRBinOp(IRBinOpKind.ADD, node.result, node.right, node.right)
                    node.right.asInt() == 2L -> IRBinOp(IRBinOpKind.ADD, node.result, node.left, node.left)
                    else -> null
                }
                IRBinOpKind.DIV -> when {
                    node.right.isOne() -> node.toAssign(node.left)
                    else -> null
                }
                else -> null
            }
            is IRPhi -> {
                // TODO move assign *after* all phi nodes, check for swap problems
                if (node.sources.all { it.value == node.sources[0].value }) {
                    return node.toAssign(node.sources[0].value)
                }

                val nonSelfSources = node.sources.filter { it.value != node.lvalue }
                check(nonSelfSources.isNotEmpty()) // Sanity check; will trigger if `x = phi(x, x, ..., x)` will be generated
                if (nonSelfSources.size == 1) {
                    return node.toAssign(nonSelfSources.single().value)
                }

                null
            }
            else -> null
        }
    }
}