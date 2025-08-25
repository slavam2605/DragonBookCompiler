package ir

import MainGrammar
import MainLexer
import compiler.frontend.CompileToIRVisitor
import compiler.frontend.SemanticAnalysisVisitor
import compiler.ir.IRPhi
import compiler.ir.IRProtoNode
import compiler.ir.IRVar
import compiler.ir.analysis.DefiniteAssignmentAnalysis
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.optimization.constant.SSCPValue
import compiler.ir.optimization.clean.CleanCFG
import compiler.ir.optimization.constant.SparseConditionalConstantPropagation
import compiler.ir.printToString
import ir.CompileToIRTestBase.Companion.PRINT_DEBUG_INFO
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import parser.UnderlineErrorListener
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object TestCompilationFlow {
    fun compileToIR(input: String): Pair<List<IRProtoNode>, SourceLocationMap> {
        val lexer = MainLexer(CharStreams.fromString(input))
        val parser = MainGrammar(CommonTokenStream(lexer)).apply {
            removeErrorListeners()
            addErrorListener(UnderlineErrorListener())
        }
        val tree = parser.program()
        assertTrue("Test program has ${parser.numberOfSyntaxErrors} parser errors") {
            parser.numberOfSyntaxErrors == 0
        }
        SemanticAnalysisVisitor().analyze(tree)
        return CompileToIRVisitor().compileToIR(tree)
    }

    fun compileToCFG(input: String): ControlFlowGraph {
        val (ir, sourceMap) = compileToIR(input)
        val cfg = ControlFlowGraph.build(ir, sourceMap)
        DefiniteAssignmentAnalysis(cfg).run()
        return cfg
    }

    fun compileToSSA(input: String): SSAControlFlowGraph {
        val cfg = compileToCFG(input)
        val ssa = SSAControlFlowGraph.transform(cfg)
        testSingleAssignmentsInSSA(ssa)
        testPhiNodeSourceSize(ssa)
        return ssa
    }

    fun compileToOptimizedSSA(input: String): Triple<SSAControlFlowGraph, SSAControlFlowGraph, Map<IRVar, Long>> {
        val ssa = compileToSSA(input)

        val cpList = mutableListOf<SparseConditionalConstantPropagation>()
        var currentStep = ssa
        var changed = true
        var stepIndex = 0
        while (changed) {
            if (PRINT_DEBUG_INFO) println("SCCP step $stepIndex")
            stepIndex++
            val initialStep = currentStep

            currentStep = SparseConditionalConstantPropagation(currentStep).let {
                cpList.add(it)
                it.run()
            }
            currentStep = CleanCFG.invoke(currentStep) as SSAControlFlowGraph

            changed = initialStep !== currentStep
        }

        val allCp = cpList.map { it.staticValues.toMap() }.reduce { a, b -> a + b }
        return Triple(ssa, currentStep, allCp
            .filterValues { it is SSCPValue.Value }
            .mapValues { (_, value) -> (value as SSCPValue.Value).value })
    }

    // -------- compilation consistency checks --------

    private fun testSingleAssignmentsInSSA(ssa: SSAControlFlowGraph) {
        val seenLValues = mutableSetOf<IRVar>()
        ssa.blocks.forEach { (_, block) ->
            block.irNodes.forEach { node ->
                node.lvalue?.let { lvalue ->
                    assertTrue(seenLValues.add(lvalue),
                        "Variable ${lvalue.printToString()} is used more than once")
                }
            }
        }
    }

    private fun testPhiNodeSourceSize(ssa: SSAControlFlowGraph) {
        ssa.blocks.forEach { (label, block) ->
            val inEdgesCount = ssa.backEdges(label).size
            block.irNodes.filterIsInstance<IRPhi>().forEach { irPhi ->
                assertEquals(
                    inEdgesCount,
                    irPhi.sources.size,
                    "Phi node ${irPhi.printToString()} has ${irPhi.sources.size} sources, but should have $inEdgesCount"
                )
            }
        }
    }
}