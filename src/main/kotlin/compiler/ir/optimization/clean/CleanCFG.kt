package compiler.ir.optimization.clean

import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ControlFlowGraph

object CleanCFG {
    fun invoke(cfg: ControlFlowGraph, ffs: FrontendFunctions<out ControlFlowGraph>): ControlFlowGraph {
        var currentStep = cfg

        var changed = true
        while (changed) {
            changed = false
            val initialStep = currentStep
            currentStep = FoldRedundantBranches(currentStep).invoke()
            currentStep = RemoveEmptyBlocks(currentStep).invoke()
            currentStep = CombineBlocks(currentStep).invoke()
            currentStep = RemoveUnreachableBlocks(currentStep).invoke()
            currentStep = RemoveUnusedAssignments(currentStep, ffs).invoke()
            if (currentStep !== initialStep) changed = true
        }

        return currentStep
    }
}