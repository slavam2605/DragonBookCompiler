package ir.interpreter

import compiler.frontend.FrontendFunctions
import compiler.frontend.FrontendConstantValue
import compiler.ir.*

abstract class BaseInterpreter<T>(
    functionName: String,
    private val arguments: List<FrontendConstantValue>,
    private val functions: FrontendFunctions<out T>,
    private val fallbackFunctionHandler: (String, List<FrontendConstantValue>) -> FrontendConstantValue,
    private val exitAfterMaxSteps: Boolean
) {
    protected val vars = mutableMapOf<IRVar, FrontendConstantValue>()
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

    abstract fun eval(): Map<IRVar, FrontendConstantValue>

    abstract fun callFunction(functionName: String, args: List<FrontendConstantValue>): FrontendConstantValue?

    protected sealed interface Command {
        class Jump(val label: IRLabel) : Command
        object Continue : Command
        object Exit : Command
    }

    protected fun getValue(value: IRValue): FrontendConstantValue = when (value) {
        is IRInt -> FrontendConstantValue.IntValue(value.value)
        is IRFloat -> FrontendConstantValue.FloatValue(value.value)
        is IRVar -> {
            val constantValue = vars[value] ?: error("Variable ${value.printToString()} is not initialized")
            check(constantValue.irType == value.type) {
                "Variable ${value.printToString()} has type ${value.type} but value has type ${constantValue.irType}"
            }
            constantValue
        }
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
                require(left.irType == right.irType) {
                    "IRBinOp requires operands of the same type, got ${left.irType} and ${right.irType}"
                }
                val result = when (node.op) {
                    IRBinOpKind.ADD -> left + right
                    IRBinOpKind.SUB -> left - right
                    IRBinOpKind.MUL -> left * right
                    IRBinOpKind.DIV -> left / right
                    IRBinOpKind.MOD -> left % right
                    IRBinOpKind.EQ -> FrontendConstantValue.IntValue(if (left == right) 1 else 0)
                    IRBinOpKind.NEQ -> FrontendConstantValue.IntValue(if (left != right) 1 else 0)
                    IRBinOpKind.GT -> FrontendConstantValue.IntValue(if (left > right) 1 else 0)
                    IRBinOpKind.GE -> FrontendConstantValue.IntValue(if (left >= right) 1 else 0)
                    IRBinOpKind.LT -> FrontendConstantValue.IntValue(if (left < right) 1 else 0)
                    IRBinOpKind.LE -> FrontendConstantValue.IntValue(if (left <= right) 1 else 0)
                }
                vars[node.result] = result
            }
            is IRNot -> {
                val value = getValue(node.value)
                vars[node.result] = FrontendConstantValue.IntValue(if ((value as FrontendConstantValue.IntValue).value == 0L) 1 else 0)
            }
            is IRConvert -> {
                val value = getValue(node.value)
                val result = when (node.result.type) {
                    IRType.INT64 -> {
                        val floatVal = (value as FrontendConstantValue.FloatValue).value
                        FrontendConstantValue.IntValue(floatVal.toLong())
                    }
                    IRType.FLOAT64 -> {
                        val intVal = (value as FrontendConstantValue.IntValue).value
                        FrontendConstantValue.FloatValue(intVal.toDouble())
                    }
                }
                vars[node.result] = result
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
                val target = if ((condition as FrontendConstantValue.IntValue).value == 0L) node.elseTarget else node.target
                return Command.Jump(target)
            }
            is IRReturn -> {
                node.value?.let {
                    vars[IntReturnValue] = getValue(it)
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
        protected val DEFAULT_FUNCTION_HANDLER: (String, List<FrontendConstantValue>) -> FrontendConstantValue = { name, _ ->
            error("Unknown function: $name")
        }

        /**
         * Synthetic variable used to store the return value of a function.
         */
        val IntReturnValue = IRVar($$"$return_value$", IRType.INT64, null)
    }
}