package compiler.ir.optimization.constant

sealed interface SSCPValue {
    data class Value(val value: Long) : SSCPValue
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
        this is Value && other is Value -> {
            if (this.value == other.value) this else Bottom
        }
        else -> error("Unhandled case: $this, $other")
    }

    operator fun compareTo(other: SSCPValue): Int {
        if (this is Value && other is Value && value != other.value) {
            error("Value($value) is not comparable with Value(${other.value})")
        }

        return classOrd().compareTo(other.classOrd())
    }

    private fun classOrd() = when (this) {
        is Top -> 2
        is Value -> 1
        is Bottom -> 0
    }
}