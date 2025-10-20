package compiler.frontend

import MainGrammar
import compiler.ir.IRProtoNode
import compiler.ir.analysis.DefiniteAssignmentAnalysis
import compiler.ir.analysis.DefiniteReturnAnalysis
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.cfg.ssa.ConvertFromSSA
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.optimization.EqualityPropagation
import compiler.ir.optimization.clean.CleanCFG
import compiler.ir.optimization.constant.ConditionalJumpValues
import compiler.ir.optimization.constant.SparseConditionalConstantPropagation
import compiler.ir.optimization.inline.InlineFunctions
import compiler.ir.optimization.valueNumbering.GlobalValueNumbering

object FrontendCompilationFlow {
    fun compileToIR(tree: MainGrammar.ProgramContext): Pair<FrontendFunctions<List<IRProtoNode>>, SourceLocationMap> {
        SemanticAnalysisVisitor().analyze(tree)
        return CompileToIRVisitor().compileToIR(tree)
    }

    fun buildCFG(irFunctions: FrontendFunctions<List<IRProtoNode>>, sourceMap: SourceLocationMap): FrontendFunctions<ControlFlowGraph> {
        return irFunctions.map { function ->
            ControlFlowGraph.build(function.value, sourceMap).also {
                DefiniteAssignmentAnalysis(it, function).run()
                DefiniteReturnAnalysis(it, function).run()
            }
        }
    }

    fun buildSSA(cfgFunctions: FrontendFunctions<ControlFlowGraph>): FrontendFunctions<SSAControlFlowGraph> {
        return cfgFunctions.map { function ->
            SSAControlFlowGraph.transform(function.value)
        }
    }

    fun optimizeSSA(ssaFunctions: FrontendFunctions<SSAControlFlowGraph>): FrontendFunctions<SSAControlFlowGraph> {
        val step1 = ssaFunctions.optimizeEachFunction()
        val step2 = InlineFunctions(step1).run()
        return step2.optimizeEachFunction()
    }

    fun convertFromSSA(ssaFunctions: FrontendFunctions<SSAControlFlowGraph>): FrontendFunctions<ControlFlowGraph> {
        return ssaFunctions.map { ConvertFromSSA(it.value).run() }
    }

    private fun FrontendFunctions<SSAControlFlowGraph>.optimizeEachFunction(): FrontendFunctions<SSAControlFlowGraph> {
        return map { function ->
            // Initial clean pass to remove unreachable blocks
            var currentStep = CleanCFG.invoke(function.value, this) as SSAControlFlowGraph
            var changed = true
            var stepIndex = 0
            while (changed) {
                stepIndex++
                val initialStep = currentStep

                currentStep = SparseConditionalConstantPropagation(currentStep, function.parameters).run()
                currentStep = CleanCFG.invoke(currentStep, this) as SSAControlFlowGraph
                currentStep = GlobalValueNumbering(currentStep).run()
                currentStep = EqualityPropagation(currentStep).invoke()
                currentStep = ConditionalJumpValues(currentStep).run()

                changed = initialStep !== currentStep
            }

            currentStep
        }
    }
}