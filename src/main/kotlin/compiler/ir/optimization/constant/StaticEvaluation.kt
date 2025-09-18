package compiler.ir.optimization.constant

import compiler.ir.IRAssign
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRFunctionCall
import compiler.ir.IRInt
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRNode
import compiler.ir.IRNot
import compiler.ir.IRPhi
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.IRReturn

private val equalComparisonOps = setOf(IRBinOpKind.EQ, IRBinOpKind.GE, IRBinOpKind.LE)
private val notEqualComparisonOps = setOf(IRBinOpKind.NEQ, IRBinOpKind.GT, IRBinOpKind.LT)

fun IRNode.evaluateSafe(rValues: List<SSCPValue>): SSCPValue {
    return try {
        evaluate(rValues)
    } catch (_: ArithmeticException) {
        SSCPValue.Top
    }
}

fun IRValue.evaluateOneValue(varValue: (IRVar) -> SSCPValue?): SSCPValue = when (this) {
    is IRVar -> varValue(this) ?: SSCPValue.Top
    is IRInt -> SSCPValue.Value(value)
    else -> error("Floats are not supported")
}

private fun IRNode.evaluate(rValues: List<SSCPValue>): SSCPValue {
    return when (this) {
        is IRAssign -> rValues[0]
        is IRBinOp -> {
            if (rValues.any { it !is SSCPValue.Value }) {
                return when {
                    op == IRBinOpKind.SUB && rvalues()[0] == rvalues()[1] -> SSCPValue.Value(0)
                    op == IRBinOpKind.MUL && rValues.any { it == SSCPValue.Value(0) } -> SSCPValue.Value(0)
                    op == IRBinOpKind.MOD && rValues[1] == SSCPValue.Value(1) -> SSCPValue.Value(0)
                    op in equalComparisonOps && rvalues()[0] == rvalues()[1] -> SSCPValue.Value(1)
                    op in notEqualComparisonOps && rvalues()[0] == rvalues()[1] -> SSCPValue.Value(0)
                    else -> if (rValues.any { it == SSCPValue.Bottom }) SSCPValue.Bottom else SSCPValue.Top
                }
            }
            when (op) {
                IRBinOpKind.ADD -> withIntValues(rValues[0], rValues[1]) { it[0] + it[1] }
                IRBinOpKind.SUB -> withIntValues(rValues[0], rValues[1]) { it[0] - it[1] }
                IRBinOpKind.MUL -> withIntValues(rValues[0], rValues[1]) { it[0] * it[1] }
                IRBinOpKind.DIV -> withIntValues(rValues[0], rValues[1]) { it[0] / it[1] }
                IRBinOpKind.MOD -> withIntValues(rValues[0], rValues[1]) { it[0] % it[1] }
                IRBinOpKind.EQ  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] == it[1] }
                IRBinOpKind.NEQ -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] != it[1] }
                IRBinOpKind.GT  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] > it[1] }
                IRBinOpKind.GE  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] >= it[1] }
                IRBinOpKind.LT  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] < it[1] }
                IRBinOpKind.LE  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] <= it[1] }
            }
        }
        is IRNot -> {
            if (rValues[0] == SSCPValue.Top) return SSCPValue.Top
            if (rValues[0] == SSCPValue.Bottom) return SSCPValue.Bottom
            withBoolValues(rValues[0]) { !it[0] }
        }
        is IRPhi -> {
            rValues.reduce(SSCPValue::times)
        }
        is IRFunctionCall -> SSCPValue.Bottom
        is IRJump, is IRJumpIfTrue, is IRReturn -> {
            error("Cannot evaluate node without lvalues: $this")
        }
    }
}

private fun withIntValues(vararg values: SSCPValue, block: (List<Long>) -> Long): SSCPValue {
    return SSCPValue.Value(block(values.map { (it as SSCPValue.Value).value }))
}

private fun withBoolValues(vararg values: SSCPValue, block: (List<Boolean>) -> Boolean): SSCPValue.Value {
    val args = values.map {
        val intValue = (it as SSCPValue.Value).value
        check(intValue == 0L || intValue == 1L) {
            "Boolean values must be either 0 or 1, got $intValue"
        }
        intValue == 1L
    }
    val result = block(args)
    return SSCPValue.Value(if (result) 1L else 0L)
}

private fun withIntValuesBoolResult(vararg values: SSCPValue, block: (List<Long>) -> Boolean): SSCPValue.Value {
    val intResult = block(values.map { (it as SSCPValue.Value).value })
    return SSCPValue.Value(if (intResult) 1L else 0L)
}