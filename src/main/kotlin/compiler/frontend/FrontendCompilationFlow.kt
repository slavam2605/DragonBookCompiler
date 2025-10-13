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
        return ssaFunctions.map { function ->
            val cpList = mutableListOf<SparseConditionalConstantPropagation>()
            val equalityList = mutableListOf<EqualityPropagation>()

            // Initial clean pass to remove unreachable blocks
            var currentStep = CleanCFG.invoke(function.value) as SSAControlFlowGraph
            var changed = true
            var stepIndex = 0
            while (changed) {
                stepIndex++
                val initialStep = currentStep

                currentStep = SparseConditionalConstantPropagation(currentStep, function.parameters).let {
                    cpList.add(it)
                    it.run()
                }
                currentStep = CleanCFG.invoke(currentStep) as SSAControlFlowGraph
                currentStep = GlobalValueNumbering(currentStep).run()
                currentStep = EqualityPropagation(currentStep).let {
                    equalityList.add(it)
                    it.invoke()
                }
                currentStep = ConditionalJumpValues(currentStep).run()

                changed = initialStep !== currentStep
            }

            currentStep
        }
    }

    fun convertFromSSA(ssaFunctions: FrontendFunctions<SSAControlFlowGraph>): FrontendFunctions<ControlFlowGraph> {
        return ssaFunctions.map { ConvertFromSSA(it.value).run() }
    }
}