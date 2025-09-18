package ir.interpreter

import compiler.frontend.FrontendFunctions
import compiler.ir.*

abstract class BaseInterpreter<T>(
    functionName: String,
    private val arguments: List<InterpretedValue>,
    private val functions: FrontendFunctions<out T>,
    private val fallbackFunctionHandler: (String, List<InterpretedValue>) -> InterpretedValue,
    private val exitAfterMaxSteps: Boolean
) {
    protected val vars = mutableMapOf<IRVar, InterpretedValue>()
    private var stepCounter = 0
    protected val currentFunction = functions[functionName]
        ?: error("Main function not found")

    init {
        val parameters = currentFunction.parameters
        check(parameters.size == arguments.size) {
            "Wrong number of arguments for function $functionName: " +
                    "expected ${parameters.size}, got ${arguments.size}"
        }

        // Initialize function parameters
        parameters.forEachIndexed { index, parameter ->
            vars[parameter] = arguments[index]
        }
    }

    abstract fun eval(): Map<IRVar, InterpretedValue>

    abstract fun callFunction(functionName: String, args: List<InterpretedValue>): InterpretedValue?

    protected sealed interface Command {
        class Jump(val label: IRLabel) : Command
        object Continue : Command
        object Exit : Command
    }

    protected fun getValue(value: IRValue): InterpretedValue = when (value) {
        is IRInt -> IntValue(value.value)
        is IRFloat -> FloatValue(value.value)
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
                    IRBinOpKind.EQ -> IntValue(if (left == right) 1 else 0)
                    IRBinOpKind.NEQ -> IntValue(if (left != right) 1 else 0)
                    IRBinOpKind.GT -> IntValue(if (left > right) 1 else 0)
                    IRBinOpKind.GE -> IntValue(if (left >= right) 1 else 0)
                    IRBinOpKind.LT -> IntValue(if (left < right) 1 else 0)
                    IRBinOpKind.LE -> IntValue(if (left <= right) 1 else 0)
                }
                vars[node.result] = result
            }
            is IRNot -> {
                val value = getValue(node.value)
                vars[node.result] = IntValue(if ((value as IntValue).value == 0L) 1 else 0)
            }
            is IRFunctionCall -> {
                val arguments = node.arguments.map { getValue(it) }
                val result = if (node.name in functions) {
                    callFunction(node.name, arguments)
                } else {
                    fallbackFunctionHandler(node.name, arguments)
                }
                if (node.result != null) vars[node.result] = result!!
            }
            is IRJump -> return Command.Jump(node.target)
            is IRJumpIfTrue -> {
                val condition = getValue(node.cond)
                val target = if ((condition as IntValue).value == 0L) node.elseTarget else node.target
                return Command.Jump(target)
            }
            is IRReturn -> {
                node.value?.let {
                    vars[ReturnValue] = getValue(it)
                }
                return Command.Exit
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
        protected val DEFAULT_FUNCTION_HANDLER: (String, List<InterpretedValue>) -> InterpretedValue = { name, _ ->
            error("Unknown function: $name")
        }

        /**
         * Synthetic variable used to store the return value of a function.
         */
        val ReturnValue = IRVar($$"$return_value$", null)
    }
}