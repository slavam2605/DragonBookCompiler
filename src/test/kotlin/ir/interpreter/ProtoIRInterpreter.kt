package ir.interpreter

import compiler.ir.*

open class ProtoIRInterpreter(
    val ir: List<IRProtoNode>,
    functionHandler: (String, List<Long>) -> Unit = DEFAULT_FUNCTION_HANDLER,
    simulateUndef: Boolean = false,
    exitAfterMaxSteps: Boolean = false
) : BaseInterpreter(functionHandler, simulateUndef, exitAfterMaxSteps) {
    private val labelMap = mutableMapOf<IRLabel, Int>()
    private var currentLine: Int = 0

    init {
        ir.forEachIndexed { index, protoNode ->
            if (protoNode is IRLabel) {
                labelMap[protoNode] = index
            }
        }
    }

    private fun findLabel(labelNode: IRLabel): Int =
        labelMap[labelNode] ?: error("Label not found: ${labelNode.name}")

    override fun eval(): Map<IRVar, Long> {
        while (currentLine < ir.size) {
            when (val command = baseEval(ir[currentLine])) {
                is Command.Jump -> currentLine = findLabel(command.label)
                is Command.Continue -> currentLine++
                is Command.Exit -> break
            }
        }
        return vars.toMap()
    }
}