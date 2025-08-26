package ir.interpreter

import compiler.ir.IRLabel
import compiler.ir.IRPhi
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class CFGInterpreter(
    val cfg: ControlFlowGraph,
    functionHandler: (String, List<Long>) -> Unit = DEFAULT_FUNCTION_HANDLER,
    simulateUndef: Boolean = false,
    exitAfterMaxSteps: Boolean = false
) : BaseInterpreter(functionHandler, simulateUndef, exitAfterMaxSteps) {
    private var jumpedFromLabel: IRLabel? = null
    private var currentLabel: IRLabel = cfg.root
    private var currentLine: Int = 0
    private val tempPhiBuffer = mutableMapOf<IRVar, Long>()

    private val currentBlock
        get() = cfg.blocks[currentLabel] ?: error("No block for label $currentLabel")

    override fun eval(): Map<IRVar, Long> {
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
        return vars.toMap()
    }
}