package compiler.ir.optimization.constant

sealed interface SSCPValue {
    sealed interface Value : SSCPValue {
        val value: Number
    }

    data class IntValue(override val value: Long) : Value
    data class FloatValue(override val value: Double) : Value
    object Top : SSCPValue
    object Bottom : SSCPValue

    /**
     * Meet operator for lattice values:
     *  - `forall a: a * Bottom = Bottom`
     *  - `forall a: a * Top = Top`
     *  - `forall constants c: c * c = c`
     *  - `forall constants c1, c2, c1 != c2: c1 * c2 = Bottom`
     */
    operator fun times(other: SSCPValue): SSCPValue = when {
        this is Bottom || other is Bottom -> Bottom
        this is Top -> other
        other is Top -> this
        this is IntValue && other is IntValue -> {
            if (this.value == other.value) this else Bottom
        }
        else -> error("Unhandled case: $this, $other")
    }

    operator fun compareTo(other: SSCPValue): Int {
        if (this is Value && other is Value && value != other.value) {
            error("${this.javaClass.simpleName}($value) is not comparable " +
                    "with ${other.javaClass.simpleName}(${other.value})")
        }

        return classOrd().compareTo(other.classOrd())
    }

    private fun classOrd() = when (this) {
        is Top -> 2
        is IntValue,
        is FloatValue -> 1
        is Bottom -> 0
    }
}