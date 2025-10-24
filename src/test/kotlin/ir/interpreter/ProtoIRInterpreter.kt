package ir.interpreter

import compiler.frontend.FrontendFunctions
import compiler.frontend.FrontendConstantValue
import compiler.ir.*
import compiler.ir.cfg.extensions.SourceLocationMap

internal class ProtoIRInterpreter(
    functionName: String,
    arguments: List<FrontendConstantValue>,
    private val functions: FrontendFunctions<List<IRProtoNode>>,
    private val fallbackFunctionHandler: (String, List<FrontendConstantValue>) -> FrontendConstantValue = DEFAULT_FUNCTION_HANDLER,
    override val sourceMap: SourceLocationMap,
    private val allocator: TestAllocator = TestAllocator(),
    private val exitAfterMaxSteps: Boolean = false
) : BaseInterpreter<List<IRProtoNode>>(functionName, arguments, functions, fallbackFunctionHandler, allocator, exitAfterMaxSteps) {
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

    override fun eval(isOuterMain: Boolean): Map<IRVar, FrontendConstantValue> {
        val ir = currentFunction.value
        while (currentLine < ir.size) {
            when (val command = baseEval(ir[currentLine])) {
                is Command.Jump -> currentLine = findLabel(command.label)
                is Command.Continue -> currentLine++
                is Command.Exit -> break
            }
        }
        if (isOuterMain) {
            allocator.checkUnfreedMemory()
        }
        return vars.toMap()
    }

    override fun callFunction(functionName: String, args: List<FrontendConstantValue>): FrontendConstantValue? {
        return ProtoIRInterpreter(
            functionName,
            args,
            functions,
            fallbackFunctionHandler,
            sourceMap,
            allocator,
            exitAfterMaxSteps
        ).eval()[IntReturnValue]
    }
}