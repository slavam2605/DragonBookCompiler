package compiler.frontend

sealed class FrontendConstantValue {
    abstract operator fun plus(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun minus(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun times(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun div(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun rem(other: FrontendConstantValue): FrontendConstantValue
    abstract operator fun compareTo(other: FrontendConstantValue): Int

    internal data class IntValue(val value: Long) : FrontendConstantValue() {
        override fun plus(other: FrontendConstantValue) = IntValue(value + (other as IntValue).value)
        override fun minus(other: FrontendConstantValue) = IntValue(value - (other as IntValue).value)
        override fun times(other: FrontendConstantValue) = IntValue(value * (other as IntValue).value)
        override fun div(other: FrontendConstantValue) = IntValue(value / (other as IntValue).value)
        override fun rem(other: FrontendConstantValue) = IntValue(value % (other as IntValue).value)
        override fun compareTo(other: FrontendConstantValue): Int = value.compareTo((other as IntValue).value)
        override fun toString(): String = value.toString()
    }

    internal data class FloatValue(val value: Double) : FrontendConstantValue() {
        override fun plus(other: FrontendConstantValue) = FloatValue(value + (other as FloatValue).value)
        override fun minus(other: FrontendConstantValue) = FloatValue(value - (other as FloatValue).value)
        override fun times(other: FrontendConstantValue) = FloatValue(value * (other as FloatValue).value)
        override fun div(other: FrontendConstantValue) = FloatValue(value / (other as FloatValue).value)
        override fun rem(other: FrontendConstantValue) = FloatValue(value % (other as FloatValue).value)
        override fun compareTo(other: FrontendConstantValue): Int = value.compareTo((other as FloatValue).value)
        override fun toString(): String = value.toString()
    }
}