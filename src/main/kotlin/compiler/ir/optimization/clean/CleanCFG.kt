package compiler.ir.optimization.clean

import compiler.ir.cfg.ControlFlowGraph

object CleanCFG {
    fun invoke(cfg: ControlFlowGraph): ControlFlowGraph {
        var currentStep = cfg

        var changed = true
        while (changed) {
            changed = false
            val initialStep = currentStep
            currentStep = RemoveEmptyBlocksChangeEdges(currentStep).invoke()
            currentStep = RemoveUnreachableBlocks(currentStep).invoke()
            if (currentStep !== initialStep) changed = true
        }

        return currentStep
    }
}