package ir

import compiler.frontend.FrontendCompilationFlow
import compiler.frontend.FrontendConstantValue
import compiler.frontend.FrontendFunctions
import compiler.ir.IRPhi
import compiler.ir.IRProtoNode
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.printToString
import parser.ParserFlow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object TestCompilationFlow {
    fun compileToIR(input: String): Pair<FrontendFunctions<List<IRProtoNode>>, SourceLocationMap> {
        val (parser, _, tree) = ParserFlow.parseString(input)
        assertTrue("Test program has ${parser.numberOfSyntaxErrors} parser errors") {
            parser.numberOfSyntaxErrors == 0
        }
        return FrontendCompilationFlow.compileToIR(tree)
    }

    fun compileToCFG(input: String): FrontendFunctions<ControlFlowGraph> {
        val (irFfs, sourceMap) = compileToIR(input)
        return FrontendCompilationFlow.buildCFG(irFfs, sourceMap)
    }

    fun compileToSSA(input: String): FrontendFunctions<SSAControlFlowGraph> {
        val cfgFfs = compileToCFG(input)
        return FrontendCompilationFlow.buildSSA(cfgFfs).also { ssaFfs ->
            ssaFfs.forEach { ssa ->
                testSingleAssignmentsInSSA(ssa.value)
                testPhiNodeSourceSize(ssa.value)
            }
        }
    }

    fun compileToOptimizedSSA(input: String): FrontendFunctions<FrontendCompilationFlow.OptimizedResult> {
        val ssa = compileToSSA(input)
        return FrontendCompilationFlow.optimizeSSA(ssa)
    }

    fun compileToOptimizedCFG(input: String): FrontendFunctions<CFGWithStaticValues> {
        val optimizedSSA = compileToOptimizedSSA(input)
        val ssaOnly = optimizedSSA.map { it.value.optimizedSSA }
        return FrontendCompilationFlow.convertFromSSA(ssaOnly).map {
            val ffWithValues = optimizedSSA[it.name]!!.value
            CFGWithStaticValues(it.value, ffWithValues.cpValues, ffWithValues.equalities)
        }
    }

    data class CFGWithStaticValues(
        val cfg: ControlFlowGraph,
        val cpValues: Map<IRVar, FrontendConstantValue>,
        val equalities: Map<IRVar, IRVar>
    )

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