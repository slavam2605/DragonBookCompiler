package ir.interpreter

import compiler.ir.*

abstract class BaseInterpreter(
    private val functionHandler: (String, List<Long>) -> Long,
    private val exitAfterMaxSteps: Boolean
) {
    protected val vars = mutableMapOf<IRVar, Long>()
    private var stepCounter = 0

    abstract fun eval(): Map<IRVar, Long>

    protected sealed interface Command {
        class Jump(val label: IRLabel) : Command
        object Continue : Command
        object Exit : Command
    }

    protected fun getValue(value: IRValue): Long = when (value) {
        is IRInt -> value.value
        is IRVar -> vars[value] ?: error("Variable ${value.printToString()} is not initialized")
    }

    protected fun baseEval(node: IRProtoNode): Command {
        incrementStep()?.let { return it }
        when (node) {
            is IRLabel -> { /* skip */ }
            is IRPhi -> {
                error("Phi nodes are not supported in the base interpreter")
            }
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
            is IRFunctionCall -> {
                val arguments = node.arguments.map { getValue(it) }
                val result = functionHandler(node.name, arguments)
                if (node.result != null) vars[node.result] = result
            }
            is IRJump -> return Command.Jump(node.target)
            is IRJumpIfTrue -> {
                val condition = getValue(node.cond)
                val target = if (condition == 0L) node.elseTarget else node.target
                return Command.Jump(target)
            }
        }
        return Command.Continue
    }

    private fun incrementStep(): Command? {
        stepCounter++
        if (stepCounter < MAX_STEPS) return null
        if (exitAfterMaxSteps) return Command.Exit
        throw ExceededEvaluationStepsException(MAX_STEPS)
    }

    companion object {
        /**
         * Maximum number of steps to execute before stopping interpretation of the program.
         * Used to prevent infinite loops in the program.
         */
        private const val MAX_STEPS = 1_000_000

        @JvmStatic
        protected val DEFAULT_FUNCTION_HANDLER: (String, List<Long>) -> Long = { name, _ ->
            error("Unknown function: $name")
        }
    }
}