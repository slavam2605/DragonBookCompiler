package compiler.ir

// values

sealed interface IRValue

data class IRVar(val name: String, val ssaVer: Int) : IRValue {
    constructor(name: String) : this(name, 0)
}

data class IRInt(val value: Long) : IRValue

// interfaces

sealed interface IRProtoNode

sealed interface IRNode : IRProtoNode

enum class IRBinOpKind { ADD, SUB, MUL, DIV, MOD, EQ, NEQ, GT, GE, LT, LE }

// implementations

data class IRLabel(val name: String) : IRProtoNode

class IRAssign(val result: IRVar, val right: IRValue) : IRNode

class IRBinOp(val op: IRBinOpKind, val result: IRVar, val left: IRValue, val right: IRValue) : IRNode

class IRNot(val result: IRVar, val value: IRValue) : IRNode

class IRJumpIfFalse(val cond: IRValue, val target: IRLabel) : IRNode

class IRJumpIfTrue(val cond: IRValue, val target: IRLabel) : IRNode

class IRJump(val target: IRLabel) : IRNode