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
import compiler.ir.optimization.EqualityPropagation
import compiler.ir.optimization.constant.SSCPValue
import compiler.ir.optimization.clean.CleanCFG
import compiler.ir.optimization.constant.ConditionalJumpValues
import compiler.ir.optimization.constant.SparseConditionalConstantPropagation
import compiler.ir.optimization.valueNumbering.GlobalValueNumbering
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

    data class OptimizedResult(
        val originalSSA: SSAControlFlowGraph,
        val optimizedSSA: SSAControlFlowGraph,
        val cpValues: Map<IRVar, Long>,
        val equalities: Map<IRVar, IRVar>
    )

    fun compileToOptimizedSSA(input: String): OptimizedResult {
        val ssa = compileToSSA(input)

        val cpList = mutableListOf<SparseConditionalConstantPropagation>()
        val equalityList = mutableListOf<EqualityPropagation>()
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
            currentStep = GlobalValueNumbering(currentStep).run()
            currentStep = EqualityPropagation(currentStep).let {
                equalityList.add(it)
                it.invoke()
            }
            currentStep = ConditionalJumpValues(currentStep).run()

            changed = initialStep !== currentStep
        }

        val (cpValues, equalities) = buildStaticValues(cpList, equalityList)
        return OptimizedResult(ssa, currentStep, cpValues, equalities)
    }

    private fun buildStaticValues(
        cpList: List<SparseConditionalConstantPropagation>,
        eqList: List<EqualityPropagation>
    ): Pair<Map<IRVar, Long>, Map<IRVar, IRVar>> {
        val cpValues = cpList
            .map { it.staticValues.toMap() }
            .reduce { a, b -> a + b }
            .filterValues { it is SSCPValue.Value }
            .mapValues { (_, value) -> (value as SSCPValue.Value).value }
            .toMutableMap()

        val equalities = eqList
            .map { it.equalities }
            .reduce { a, b -> a + b }

        equalities.forEach { (var1, var2) ->
            check(cpValues[var1] == null)
            cpValues[var1] = cpValues[var2] ?: return@forEach
        }

        return cpValues to equalities
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