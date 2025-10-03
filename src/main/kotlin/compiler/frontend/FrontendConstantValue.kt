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
            return IntValue(value + (other as IntValue).value)
        }

        override fun minus(other: FrontendConstantValue): FrontendConstantValue {
            return IntValue(value - (other as IntValue).value)
        }

        override fun times(other: FrontendConstantValue): FrontendConstantValue {
            return IntValue(value * (other as IntValue).value)
        }

        override fun div(other: FrontendConstantValue): FrontendConstantValue {
            return IntValue(value / (other as IntValue).value)
        }

        override fun rem(other: FrontendConstantValue): FrontendConstantValue {
            return IntValue(value % (other as IntValue).value)
        }

        override fun compareTo(other: FrontendConstantValue): Int {
            return value.compareTo((other as IntValue).value)
        }

        override fun toString(): String = value.toString()

        override val irType = IRType.INT64
    }

    internal data class FloatValue(val value: Double) : FrontendConstantValue() {
        override fun plus(other: FrontendConstantValue): FrontendConstantValue {
            return FloatValue(value + (other as FloatValue).value)
        }

        override fun minus(other: FrontendConstantValue): FrontendConstantValue {
            return FloatValue(value - (other as FloatValue).value)
        }

        override fun times(other: FrontendConstantValue): FrontendConstantValue {
            return FloatValue(value * (other as FloatValue).value)
        }

        override fun div(other: FrontendConstantValue): FrontendConstantValue {
            return FloatValue(value / (other as FloatValue).value)
        }

        override fun rem(other: FrontendConstantValue): FrontendConstantValue {
            return FloatValue(value % (other as FloatValue).value)
        }

        override fun compareTo(other: FrontendConstantValue): Int {
            return value.compareTo((other as FloatValue).value)
        }

        override fun toString(): String = value.toString()

        override val irType = IRType.FLOAT64
    }

    abstract val irType: IRType
}