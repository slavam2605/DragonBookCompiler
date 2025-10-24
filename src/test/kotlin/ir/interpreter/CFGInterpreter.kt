package ir.interpreter

import compiler.frontend.FrontendFunctions
import compiler.frontend.FrontendConstantValue
import compiler.ir.IRLabel
import compiler.ir.IRPhi
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.SourceLocationMap

internal class CFGInterpreter(
    functionName: String,
    arguments: List<FrontendConstantValue>,
    private val functions: FrontendFunctions<out ControlFlowGraph>,
    private val fallbackFunctionHandler: (String, List<FrontendConstantValue>) -> FrontendConstantValue = DEFAULT_FUNCTION_HANDLER,
    private val allocator: TestAllocator = TestAllocator(),
    private val exitAfterMaxSteps: Boolean = false
) : BaseInterpreter<ControlFlowGraph>(functionName, arguments, functions, fallbackFunctionHandler, allocator, exitAfterMaxSteps) {
    private val cfg = functions[functionName]?.value ?: error("Function $functionName not found")
    private var jumpedFromLabel: IRLabel? = null
    private var currentLabel: IRLabel = cfg.root
    private var currentLine: Int = 0
    private val tempPhiBuffer = mutableMapOf<IRVar, FrontendConstantValue>()

    override val sourceMap: SourceLocationMap = SourceLocationMap.copyMap(cfg)

    private val currentBlock
        get() = cfg.blocks[currentLabel] ?: error("No block for label $currentLabel")

    override fun eval(isOuterMain: Boolean): Map<IRVar, FrontendConstantValue> {
        var isInPhiPrefix = true
        while (currentLine < currentBlock.irNodes.size) {
            val currentNode = currentBlock.irNodes[currentLine]
            if (isInPhiPrefix) {
                if (currentNode is IRPhi) {
                    val jumpedFromLabel = jumpedFromLabel ?: error("Phi nodes in the entry block")
                    val targetPhiSource = currentNode.getSourceValue(jumpedFromLabel)
                    val targetValue = getValue(targetPhiSource)
                    val oldBufferValue = tempPhiBuffer.put(currentNode.result, targetValue)
                    check(oldBufferValue == null) { "Duplicate phi node result ${currentNode.result}" }
                    currentLine++
                    continue
                }

                // Copy all buffered phi-values and clear the buffer
                tempPhiBuffer.forEach { (irVar, value) ->
                    vars[irVar] = value
                }
                tempPhiBuffer.clear()
                isInPhiPrefix = false
            }

            when (val command = baseEval(currentNode)) {
                is Command.Jump -> {
                    jumpedFromLabel = currentLabel
                    currentLabel = command.label
                    currentLine = 0
                    isInPhiPrefix = true
                }
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
        return CFGInterpreter(
            functionName,
            args,
            functions,
            fallbackFunctionHandler,
            allocator,
            exitAfterMaxSteps
        ).eval()[IntReturnValue]
    }
}