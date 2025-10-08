package compiler.ir.optimization.constant

import compiler.ir.IRAssign
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRConvert
import compiler.ir.IRFloat
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
import compiler.ir.IRType

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
    is IRInt -> SSCPValue.IntValue(value)
    is IRFloat -> SSCPValue.FloatValue(value)
}

private fun IRNode.evaluate(rValues: List<SSCPValue>): SSCPValue {
    return when (this) {
        is IRAssign -> rValues[0]
        is IRBinOp -> {
            when {
                rValues.all { it is SSCPValue.IntValue } -> {
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

                rValues.all { it is SSCPValue.FloatValue } -> {
                    when (op) {
                        IRBinOpKind.ADD -> withFloatValues(rValues[0], rValues[1]) { it[0] + it[1] }
                        IRBinOpKind.SUB -> withFloatValues(rValues[0], rValues[1]) { it[0] - it[1] }
                        IRBinOpKind.MUL -> withFloatValues(rValues[0], rValues[1]) { it[0] * it[1] }
                        IRBinOpKind.DIV -> withFloatValues(rValues[0], rValues[1]) { it[0] / it[1] }
                        IRBinOpKind.MOD -> withFloatValues(rValues[0], rValues[1]) { it[0] % it[1] }
                        IRBinOpKind.EQ  -> withFloatValuesBoolResult(rValues[0], rValues[1]) { it[0] == it[1] }
                        IRBinOpKind.NEQ -> withFloatValuesBoolResult(rValues[0], rValues[1]) { it[0] != it[1] }
                        IRBinOpKind.GT  -> withFloatValuesBoolResult(rValues[0], rValues[1]) { it[0] > it[1] }
                        IRBinOpKind.GE  -> withFloatValuesBoolResult(rValues[0], rValues[1]) { it[0] >= it[1] }
                        IRBinOpKind.LT  -> withFloatValuesBoolResult(rValues[0], rValues[1]) { it[0] < it[1] }
                        IRBinOpKind.LE  -> withFloatValuesBoolResult(rValues[0], rValues[1]) { it[0] <= it[1] }
                    }
                }

                else -> {
                    when {
                        op == IRBinOpKind.SUB && rvalues()[0] == rvalues()[1] -> lvalue.typedZero()
                        op == IRBinOpKind.MUL && rValues.any { it.isZero() } -> lvalue.typedZero()
                        op == IRBinOpKind.MOD && rValues[1] == SSCPValue.IntValue(1) -> SSCPValue.IntValue(0)
                        op == IRBinOpKind.MOD && rValues[1] == SSCPValue.IntValue(-1) -> SSCPValue.IntValue(0)
                        op in equalComparisonOps && rvalues()[0] == rvalues()[1] -> SSCPValue.IntValue(1)
                        op in notEqualComparisonOps && rvalues()[0] == rvalues()[1] -> SSCPValue.IntValue(0)
                        else -> if (rValues.any { it == SSCPValue.Bottom }) SSCPValue.Bottom else SSCPValue.Top
                    }
                }
            }
        }

        is IRNot -> {
            if (rValues[0] == SSCPValue.Top) return SSCPValue.Top
            if (rValues[0] == SSCPValue.Bottom) return SSCPValue.Bottom
            check(rValues[0] is SSCPValue.IntValue) {
                "Cannot evaluate 'not' on non-int value: ${rValues[0]}"
            }
            withBoolValues(rValues[0]) { !it[0] }
        }

        is IRConvert -> {
            val sourceValue = rValues[0]
            when {
                sourceValue == SSCPValue.Top -> SSCPValue.Top
                sourceValue == SSCPValue.Bottom -> SSCPValue.Bottom
                result.type == IRType.INT64 -> {
                    check(sourceValue is SSCPValue.FloatValue) {
                        "Cannot convert non-float value to int: $sourceValue"
                    }
                    SSCPValue.IntValue(sourceValue.value.toLong())
                }
                result.type == IRType.FLOAT64 -> {
                    check(sourceValue is SSCPValue.IntValue) {
                        "Cannot convert non-int value to float: $sourceValue"
                    }
                    SSCPValue.FloatValue(sourceValue.value.toDouble())
                }
                else -> error("Unexpected target type: ${result.type}")
            }
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

private fun IRValue.typedZero() = when (this.type) {
    IRType.INT64 -> SSCPValue.IntValue(0L)
    IRType.FLOAT64 -> SSCPValue.FloatValue(0.0)
}

private fun SSCPValue.isZero() = when (this) {
    is SSCPValue.IntValue -> value == 0L
    is SSCPValue.FloatValue -> value == 0.0
    is SSCPValue.Top, is SSCPValue.Bottom -> false
}

private fun withIntValues(vararg values: SSCPValue, block: (List<Long>) -> Long): SSCPValue.IntValue {
    return SSCPValue.IntValue(block(values.map { (it as SSCPValue.IntValue).value }))
}

private fun withFloatValues(vararg values: SSCPValue, block: (List<Double>) -> Double): SSCPValue.FloatValue {
    return SSCPValue.FloatValue(block(values.map { (it as SSCPValue.FloatValue).value }))
}

private fun withBoolValues(vararg values: SSCPValue, block: (List<Boolean>) -> Boolean): SSCPValue.IntValue {
    val args = values.map {
        val intValue = (it as SSCPValue.IntValue).value
        check(intValue == 0L || intValue == 1L) {
            "Boolean values must be either 0 or 1, got $intValue"
        }
        intValue == 1L
    }
    val result = block(args)
    return SSCPValue.IntValue(if (result) 1L else 0L)
}

private fun withIntValuesBoolResult(vararg values: SSCPValue, block: (List<Long>) -> Boolean): SSCPValue.IntValue {
    val intResult = block(values.map { (it as SSCPValue.IntValue).value })
    return SSCPValue.IntValue(if (intResult) 1L else 0L)
}

private fun withFloatValuesBoolResult(vararg values: SSCPValue, block: (List<Double>) -> Boolean): SSCPValue.IntValue {
    val intResult = block(values.map { (it as SSCPValue.FloatValue).value })
    return SSCPValue.IntValue(if (intResult) 1L else 0L)
}