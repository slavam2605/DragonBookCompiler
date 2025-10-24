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

    internal data class PointerValue(val address: Long, val targetType: IRType) : FrontendConstantValue() {
        override fun plus(other: FrontendConstantValue): FrontendConstantValue {
            return PointerValue(address + (other as IntValue).value, targetType)
        }

        override fun minus(other: FrontendConstantValue): FrontendConstantValue {
            return PointerValue(address - (other as IntValue).value, targetType)
        }

        override fun times(other: FrontendConstantValue): FrontendConstantValue {
            error("Cannot multiply pointer values")
        }

        override fun div(other: FrontendConstantValue): FrontendConstantValue {
            error("Cannot divide pointer values")
        }

        override fun rem(other: FrontendConstantValue): FrontendConstantValue {
            error("Cannot modulus pointer values")
        }

        override fun compareTo(other: FrontendConstantValue): Int {
            return address.compareTo((other as PointerValue).address)
        }

        override fun toString(): String = "$$address"

        override val irType = IRType.PTR(targetType)
    }

    abstract val irType: IRType
}