package ir.interpreter

import compiler.ir.IRLabel
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class CFGInterpreter(val cfg: ControlFlowGraph) : BaseInterpreter() {
    private var currentLabel: IRLabel = cfg.root
    private var currentLine: Int = 0

    private val currentBlock
        get() = cfg.blocks[currentLabel] ?: error("No block for label $currentLabel")

    override fun eval(): Map<IRVar, Long> {
        while (currentLine < currentBlock.irNodes.size) {
            when (val command = baseEval(currentBlock.irNodes[currentLine])) {
                is Command.Jump -> {
                    currentLabel = command.label
                    currentLine = 0
                }
                is Command.Continue -> currentLine++
            }
        }
        return vars.toMap()
    }
}