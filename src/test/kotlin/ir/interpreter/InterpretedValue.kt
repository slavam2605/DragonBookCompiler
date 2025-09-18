package ir.interpreter

sealed class InterpretedValue {
    abstract operator fun plus(other: InterpretedValue): InterpretedValue
    abstract operator fun minus(other: InterpretedValue): InterpretedValue
    abstract operator fun times(other: InterpretedValue): InterpretedValue
    abstract operator fun div(other: InterpretedValue): InterpretedValue
    abstract operator fun rem(other: InterpretedValue): InterpretedValue
    abstract operator fun compareTo(other: InterpretedValue): Int
}

internal data class IntValue(val value: Long) : InterpretedValue() {
    override fun plus(other: InterpretedValue) = IntValue(value + (other as IntValue).value)
    override fun minus(other: InterpretedValue) = IntValue(value - (other as IntValue).value)
    override fun times(other: InterpretedValue) = IntValue(value * (other as IntValue).value)
    override fun div(other: InterpretedValue) = IntValue(value / (other as IntValue).value)
    override fun rem(other: InterpretedValue) = IntValue(value % (other as IntValue).value)
    override fun compareTo(other: InterpretedValue): Int = value.compareTo((other as IntValue).value)
    override fun toString(): String = value.toString()
}

internal data class FloatValue(val value: Double) : InterpretedValue() {
    override fun plus(other: InterpretedValue) = FloatValue(value + (other as FloatValue).value)
    override fun minus(other: InterpretedValue) = FloatValue(value - (other as FloatValue).value)
    override fun times(other: InterpretedValue) = FloatValue(value * (other as FloatValue).value)
    override fun div(other: InterpretedValue) = FloatValue(value / (other as FloatValue).value)
    override fun rem(other: InterpretedValue) = FloatValue(value % (other as FloatValue).value)
    override fun compareTo(other: InterpretedValue): Int = value.compareTo((other as FloatValue).value)
    override fun toString(): String = value.toString()
}