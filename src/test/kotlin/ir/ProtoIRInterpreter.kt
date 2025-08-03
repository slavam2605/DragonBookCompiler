package ir

import compiler.ir.*

class ProtoIRInterpreter(val ir: List<IRProtoNode>) {
    private val labelMap = mutableMapOf<IRLabel, Int>()
    private val vars = mutableMapOf<IRVar, Long>()
    private var currentLine: Int = 0

    init {
        ir.forEachIndexed { index, protoNode ->
            if (protoNode is IRLabel) {
                labelMap[protoNode] = index
            }
        }
    }

    private fun getValue(value: IRValue): Long = when (value) {
        is IRInt -> value.value
        is IRVar -> vars[value] ?: error("Variable ${value.printToString()} is not initialized")
    }

    private fun findLabel(labelNode: IRLabel): Int =
        labelMap[labelNode] ?: error("Label not found: ${labelNode.name}")

    fun eval(): Map<IRVar, Long> {
        currentLine = 0
        while (currentLine < ir.size) {
            when (val node = ir[currentLine]) {
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
                is IRJump -> {
                    val targetLine = findLabel(node.target)
                    currentLine = targetLine
                    continue
                }
                is IRJumpIfTrue -> {
                    val condition = getValue(node.cond)
                    if (condition != 0L) {
                        val targetLine = findLabel(node.target)
                        currentLine = targetLine
                        continue
                    }
                }
                is IRJumpIfFalse -> {
                    val condition = getValue(node.cond)
                    if (condition == 0L) {
                        val targetLine = findLabel(node.target)
                        currentLine = targetLine
                        continue
                    }
                }
            }
            currentLine++
        }
        return vars.toMap()
    }
}