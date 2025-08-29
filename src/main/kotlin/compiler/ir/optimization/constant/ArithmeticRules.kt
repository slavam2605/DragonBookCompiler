package compiler.ir.optimization.constant

import compiler.ir.IRAssign
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRInt
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue

object ArithmeticRules {
    private fun IRValue.asInt() = (this as? IRInt)?.value

    private fun IRNode.toAssign(value: IRValue) = IRAssign(lvalue!!, value)

    fun simplifyNode(node: IRNode): IRNode? {
        return when (node) {
            is IRBinOp -> when (node.op) {
                IRBinOpKind.ADD -> when {
                    node.left.asInt() == 0L -> node.toAssign(node.right)
                    node.right.asInt() == 0L -> node.toAssign(node.left)
                    else -> null
                }
                IRBinOpKind.SUB -> when {
                    node.right.asInt() == 0L -> node.toAssign(node.left)
                    node.left == node.right -> node.toAssign(IRInt(0))
                    else -> null
                }
                IRBinOpKind.MUL -> when {
                    node.left.asInt() == 0L || node.right.asInt() == 0L -> node.toAssign(IRInt(0))
                    node.left.asInt() == 1L -> node.toAssign(node.right)
                    node.right.asInt() == 1L -> node.toAssign(node.left)
                    // TODO maybe should be replaced by strength reduction
                    node.left.asInt() == 2L -> IRBinOp(IRBinOpKind.ADD, node.result, node.right, node.right)
                    node.right.asInt() == 2L -> IRBinOp(IRBinOpKind.ADD, node.result, node.left, node.left)
                    else -> null
                }
                IRBinOpKind.DIV -> when {
                    node.right.asInt() == 1L -> node.toAssign(node.left)
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