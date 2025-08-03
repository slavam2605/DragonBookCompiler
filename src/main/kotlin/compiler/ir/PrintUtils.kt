package compiler.ir

fun IRLabel.printToString(): String = name

fun IRValue.printToString(): String = when (this) {
    is IRVar -> "$name.${ssaVer}"
    is IRInt -> value.toString()
}

fun IRBinOpKind.printToString(): String = when (this) {
    IRBinOpKind.ADD -> "+"
    IRBinOpKind.SUB -> "-"
    IRBinOpKind.MUL -> "*"
    IRBinOpKind.DIV -> "/"
    IRBinOpKind.MOD -> "%"
    IRBinOpKind.EQ -> "=="
    IRBinOpKind.NEQ -> "!="
    IRBinOpKind.GT -> ">"
    IRBinOpKind.GE -> ">="
    IRBinOpKind.LT -> "<"
    IRBinOpKind.LE -> "<="
}

fun IRProtoNode.printToString(): String = when (this) {
    is IRLabel -> "$name:"
    is IRAssign -> "${result.printToString()} = ${right.printToString()}"
    is IRBinOp -> "${result.printToString()} = ${left.printToString()} ${op.printToString()} ${right.printToString()}"
    is IRNot -> "${result.printToString()} = ! ${value.printToString()}"
    is IRJump -> "jump ${target.printToString()}"
    is IRJumpIfFalse -> "jump-if-false ${cond.printToString()} ${target.printToString()}"
    is IRJumpIfTrue -> "jump-if-true ${cond.printToString()} ${target.printToString()}"
}