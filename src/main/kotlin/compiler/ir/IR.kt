package compiler.ir

// values

enum class IRType {
    INT64, FLOAT64;

    override fun toString(): String {
        return when (this) {
            INT64 -> "i64"
            FLOAT64 -> "f64"
        }
    }
}

sealed interface IRValue {
    val type: IRType
}

class IRVar(val name: String, val ssaVer: Int, override val type: IRType, val sourceName: String?) : IRValue {
    constructor(name: String, type: IRType, sourceName: String?) : this(name, 0, type, sourceName)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return ssaVer == (other as IRVar).ssaVer && name == other.name
    }

    override fun hashCode(): Int {
        return 31 * ssaVer + name.hashCode()
    }

    override fun toString(): String {
        return printToString()
    }
}

data class IRInt(val value: Long) : IRValue {
    override val type: IRType = IRType.INT64
}

data class IRFloat(val value: Double) : IRValue {
    override val type: IRType = IRType.FLOAT64
}

// interfaces

sealed interface IRProtoNode

sealed interface IRNode : IRProtoNode {
    val lvalue: IRVar?
    fun rvalues(): List<IRValue>
    fun transform(transformer: IRTransformer): IRNode
}

sealed interface IRJumpNode : IRNode {
    fun labels(): List<IRLabel>
}

enum class IRBinOpKind { ADD, SUB, MUL, DIV, MOD, EQ, NEQ, GT, GE, LT, LE }

// implementations

data class IRLabel(val name: String) : IRProtoNode

data class IRSource(val from: IRLabel, val value: IRValue)

data class IRPhi(val result: IRVar, val sources: List<IRSource>) : IRNode {
    override val lvalue get() = result
    override fun rvalues() = sources.map { it.value }
    override fun transform(transformer: IRTransformer) = IRPhi(
        transformer.transformLValue(result),
        sources.mapIndexed { index, (from, value) -> IRSource(from, transformer.transformRValue(this, index, value)) }
    )

    fun getSourceValue(from: IRLabel): IRValue {
        return sources.find { it.from == from }?.value
            ?: error("Source with label $from not found")
    }

    fun replaceSourceValue(from: IRLabel, newValue: IRVar): IRPhi {
        return IRPhi(result, sources.toMutableList().apply {
            val index = sources.indexOfFirst { it.from == from }
            check(index != -1) { "Source with label $from not found" }
            this[index] = IRSource(from, newValue)
        })
    }
}

data class IRAssign(val result: IRVar, val right: IRValue) : IRNode {
    init {
        check(result.type == right.type) {
            "Different types in IRAssign: ${result.type} != ${right.type} " +
                    "(variables ${result.printToString()} and ${right.printToString()})"
        }
    }

    override val lvalue get() = result
    override fun rvalues() = listOf(right)
    override fun transform(transformer: IRTransformer) = IRAssign(
        transformer.transformLValue(result),
        transformer.transformRValue(this, 0, right)
    )
}

data class IRBinOp(val op: IRBinOpKind, val result: IRVar, val left: IRValue, val right: IRValue) : IRNode {
    init {
        require(left.type == right.type) {
            "IRBinOp requires operands of the same type, got left: ${left.type} (${left.printToString()}) " +
                    "and right: ${right.type} (${right.printToString()})"
        }
    }

    override val lvalue get() = result
    override fun rvalues() = listOf(left, right)
    override fun transform(transformer: IRTransformer) = IRBinOp(
        op,
        transformer.transformLValue(result),
        transformer.transformRValue(this, 0, left),
        transformer.transformRValue(this, 1, right)
    )
}

data class IRNot(val result: IRVar, val value: IRValue) : IRNode {
    override val lvalue get() = result
    override fun rvalues() = listOf(value)
    override fun transform(transformer: IRTransformer) = IRNot(
        transformer.transformLValue(result),
        transformer.transformRValue(this, 0, value)
    )
}

data class IRConvert(val result: IRVar, val value: IRValue) : IRNode {
    init {
        require(result.type != value.type) { "IRConvert requires different source and target types" }
    }

    override val lvalue get() = result
    override fun rvalues() = listOf(value)
    override fun transform(transformer: IRTransformer) = IRConvert(
        transformer.transformLValue(result),
        transformer.transformRValue(this, 0, value)
    )
}

data class IRFunctionCall(val name: String, val result: IRVar?, val arguments: List<IRValue>) : IRNode {
    override val lvalue get() = result
    override fun rvalues() = arguments
    override fun transform(transformer: IRTransformer) = IRFunctionCall(
        name,
        result?.let { transformer.transformLValue(it) },
        arguments.mapIndexed { index, value -> transformer.transformRValue(this, index, value) }
    )
}

data class IRJumpIfTrue(val cond: IRValue, val target: IRLabel, val elseTarget: IRLabel) : IRJumpNode {
    override val lvalue get() = null
    override fun rvalues() = listOf(cond)
    override fun labels() = listOf(target, elseTarget)
    override fun transform(transformer: IRTransformer) = IRJumpIfTrue(
        transformer.transformRValue(this, 0, cond),
        transformer.transformLabel(target),
        transformer.transformLabel(elseTarget)
    )
}

data class IRJump(val target: IRLabel) : IRJumpNode {
    override val lvalue get() = null
    override fun rvalues() = emptyList<IRValue>()
    override fun labels() = listOf(target)
    override fun transform(transformer: IRTransformer) = IRJump(
        transformer.transformLabel(target)
    )
}

data class IRReturn(val value: IRValue?) : IRJumpNode {
    override val lvalue get() = null
    override fun rvalues() = listOfNotNull(value)
    override fun labels() = emptyList<IRLabel>()
    override fun transform(transformer: IRTransformer): IRNode = IRReturn(
        value?.let { transformer.transformRValue(this, 0, it) }
    )
}