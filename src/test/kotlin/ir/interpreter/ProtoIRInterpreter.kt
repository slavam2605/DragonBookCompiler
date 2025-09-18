package ir.interpreter

import compiler.frontend.FrontendFunctions
import compiler.ir.*

open class ProtoIRInterpreter(
    functionName: String,
    arguments: List<InterpretedValue>,
    private val functions: FrontendFunctions<List<IRProtoNode>>,
    private val fallbackFunctionHandler: (String, List<InterpretedValue>) -> InterpretedValue = DEFAULT_FUNCTION_HANDLER,
    private val exitAfterMaxSteps: Boolean = false
) : BaseInterpreter<List<IRProtoNode>>(functionName, arguments, functions, fallbackFunctionHandler, exitAfterMaxSteps) {
    private val labelMap = mutableMapOf<IRLabel, Int>()
    private var currentLine: Int = 0

    init {
        currentFunction.value.forEachIndexed { index, protoNode ->
            if (protoNode is IRLabel) {
                labelMap[protoNode] = index
            }
        }
    }

    private fun findLabel(labelNode: IRLabel): Int =
        labelMap[labelNode] ?: error("Label not found: ${labelNode.name}")

    override fun eval(): Map<IRVar, InterpretedValue> {
        val ir = currentFunction.value
        while (currentLine < ir.size) {
            when (val command = baseEval(ir[currentLine])) {
                is Command.Jump -> currentLine = findLabel(command.label)
                is Command.Continue -> currentLine++
                is Command.Exit -> break
            }
        }
        return vars.toMap()
    }

    override fun callFunction(functionName: String, args: List<InterpretedValue>): InterpretedValue? {
        return ProtoIRInterpreter(
            functionName,
            args,
            functions,
            fallbackFunctionHandler,
            exitAfterMaxSteps
        ).eval()[ReturnValue]
    }
}