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
import compiler.ir.cfg.SourceLocationMap
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.optimization.ConstantPropagation
import compiler.ir.optimization.ConstantPropagation.SSCPValue
import compiler.ir.printToString
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

    fun compileToOptimizedSSA(input: String): Pair<SSAControlFlowGraph, Map<IRVar, Long>> {
        val ssa = compileToSSA(input)
        val cp = ConstantPropagation()
        val optimized = cp.run(ssa)
        return optimized to cp.values
            .filterValues { it is SSCPValue.Value }
            .mapValues { (_, value) -> (value as SSCPValue.Value).value }
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