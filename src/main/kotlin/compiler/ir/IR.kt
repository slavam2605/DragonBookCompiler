package compiler.ir

// values

sealed interface IRValue

data class IRVar(val name: String, val ssaVer: Int) : IRValue {
    constructor(name: String) : this(name, 0)
}

data class IRInt(val value: Long) : IRValue

// interfaces

sealed interface IRProtoNode

sealed interface IRNode : IRProtoNode {
    fun lvalues(): List<IRVar>
    fun rvalues(): List<IRValue>
    fun transform(transformer: IRTransformer): IRNode
}

sealed interface IRJumpNode : IRNode {
    fun labels(): List<IRLabel>
}

enum class IRBinOpKind { ADD, SUB, MUL, DIV, MOD, EQ, NEQ, GT, GE, LT, LE }

// implementations

data class IRLabel(val name: String) : IRProtoNode

class IRAssign(val result: IRVar, val right: IRValue) : IRNode {
    override fun lvalues() = listOf(result)
    override fun rvalues() = listOf(right)
    override fun transform(transformer: IRTransformer) = IRAssign(
        transformer.transformLValue(result),
        transformer.transformRValue(right)
    )
}

class IRBinOp(val op: IRBinOpKind, val result: IRVar, val left: IRValue, val right: IRValue) : IRNode {
    override fun lvalues() = listOf(result)
    override fun rvalues() = listOf(left, right)
    override fun transform(transformer: IRTransformer) = IRBinOp(
        op,
        transformer.transformLValue(result),
        transformer.transformRValue(left),
        transformer.transformRValue(right)
    )
}

class IRNot(val result: IRVar, val value: IRValue) : IRNode {
    override fun lvalues() = listOf(result)
    override fun rvalues() = listOf(value)
    override fun transform(transformer: IRTransformer) = IRNot(
        transformer.transformLValue(result),
        transformer.transformRValue(value)
    )
}

class IRJumpIfTrue(val cond: IRValue, val target: IRLabel, val elseTarget: IRLabel) : IRJumpNode {
    override fun lvalues() = emptyList<IRVar>()
    override fun rvalues() = listOf(cond)
    override fun labels() = listOf(target, elseTarget)
    override fun transform(transformer: IRTransformer) = IRJumpIfTrue(
        transformer.transformRValue(cond),
        transformer.transformLabel(target),
        transformer.transformLabel(elseTarget)
    )
}

class IRJump(val target: IRLabel) : IRJumpNode {
    override fun lvalues() = emptyList<IRVar>()
    override fun rvalues() = emptyList<IRValue>()
    override fun labels() = listOf(target)
    override fun transform(transformer: IRTransformer) = IRJump(
        transformer.transformLabel(target)
    )
}