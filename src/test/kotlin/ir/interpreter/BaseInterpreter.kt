package ir.interpreter

import compiler.ir.IRAssign
import compiler.ir.IRBinOp
import compiler.ir.IRBinOpKind
import compiler.ir.IRInt
import compiler.ir.IRJump
import compiler.ir.IRJumpIfFalse
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRLabel
import compiler.ir.IRNot
import compiler.ir.IRProtoNode
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.printToString

abstract class BaseInterpreter {
    protected val vars = mutableMapOf<IRVar, Long>()

    abstract fun eval(): Map<IRVar, Long>

    protected sealed interface Command {
        class Jump(val label: IRLabel) : Command
        object Continue : Command
    }

    protected fun getValue(value: IRValue): Long = when (value) {
        is IRInt -> value.value
        is IRVar -> vars[value] ?: error("Variable ${value.printToString()} is not initialized")
    }

    protected fun baseEval(node: IRProtoNode): Command {
        when (node) {
            is IRLabel -> { /* skip */ }
            is IRAssign -> {
                vars[node.result] = getValue(node.right)
            }
            is IRBinOp -> {
                val left = getValue(node.left)
                val right = getValue(node.right)
                val result = when (node.op) {
                    IRBinOpKind.ADD -> left + right
                    IRBinOpKind.SUB -> left - right
                    IRBinOpKind.MUL -> left * right
                    IRBinOpKind.DIV -> left / right
                    IRBinOpKind.MOD -> left % right
                    IRBinOpKind.EQ -> if (left == right) 1 else 0
                    IRBinOpKind.NEQ -> if (left != right) 1 else 0
                    IRBinOpKind.GT -> if (left > right) 1 else 0
                    IRBinOpKind.GE -> if (left >= right) 1 else 0
                    IRBinOpKind.LT -> if (left < right) 1 else 0
                    IRBinOpKind.LE -> if (left <= right) 1 else 0
                }
                vars[node.result] = result
            }
            is IRNot -> {
                val value = getValue(node.value)
                vars[node.result] = if (value == 0L) 1 else 0
            }
            is IRJump -> return Command.Jump(node.target)
            is IRJumpIfTrue -> {
                val condition = getValue(node.cond)
                if (condition != 0L) {
                    return Command.Jump(node.target)
                }
            }
            is IRJumpIfFalse -> {
                val condition = getValue(node.cond)
                if (condition == 0L) {
                    return Command.Jump(node.target)
                }
            }
        }
        return Command.Continue
    }
}