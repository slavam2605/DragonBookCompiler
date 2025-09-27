package compiler.frontend

import compiler.ir.IRType

sealed class FrontendConstantValue {
    abstract operator fun plus(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun minus(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun times(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun div(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun rem(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun compareTo(other: FrontendConstantValue): Int

    internal data class IntValue(val value: Long) : FrontendConstantValue() {
        override fun plus(other: FrontendConstantValue): FrontendConstantValue {
            if (other is IntValue) {
                return IntValue(value + other.value)
            }
            return FloatValue(value + other.floatValue)
        }

        override fun minus(other: FrontendConstantValue): FrontendConstantValue {
            if (other is IntValue) {
                return IntValue(value - other.value)
            }
            return FloatValue(value - other.floatValue)
        }

        override fun times(other: FrontendConstantValue): FrontendConstantValue {
            if (other is IntValue) {
                return IntValue(value * other.value)
            }
            return FloatValue(value * other.floatValue)
        }

        override fun div(other: FrontendConstantValue): FrontendConstantValue {
            if (other is IntValue) {
                return IntValue(value / other.value)
            }
            return FloatValue(value / other.floatValue)
        }

        override fun rem(other: FrontendConstantValue): FrontendConstantValue {
            if (other is IntValue) {
                return IntValue(value % other.value)
            }
            return FloatValue(value % other.floatValue)
        }

        override fun compareTo(other: FrontendConstantValue): Int {
            if (other is IntValue) {
                return value.compareTo(other.value)
            }
            return floatValue.compareTo(other.floatValue)
        }

        override fun toString(): String = value.toString()

        override val floatValue = value.toDouble()
        override val irType = IRType.INT64
    }

    internal data class FloatValue(val value: Double) : FrontendConstantValue() {
        override fun plus(other: FrontendConstantValue) = FloatValue(value + other.floatValue)
        override fun minus(other: FrontendConstantValue) = FloatValue(value - other.floatValue)
        override fun times(other: FrontendConstantValue) = FloatValue(value * other.floatValue)
        override fun div(other: FrontendConstantValue) = FloatValue(value / other.floatValue)
        override fun rem(other: FrontendConstantValue) = FloatValue(value % other.floatValue)
        override fun compareTo(other: FrontendConstantValue): Int = value.compareTo(other.floatValue)
        override fun toString(): String = value.toString()

        override val floatValue = value
        override val irType = IRType.FLOAT64
    }

    abstract val floatValue: Double

    abstract val irType: IRType
}