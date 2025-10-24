package ir

import compiler.frontend.FrontendCompilationFlow
import compiler.frontend.FrontendFunctions
import compiler.ir.IRFunctionCall
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
        val (_, treeSupplier) = ParserFlow.parseString(input)
        return FrontendCompilationFlow.compileToIR(treeSupplier.get())
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
                testSourceMap(ssa.value)
            }
        }
    }

    fun compileToOptimizedSSA(input: String): FrontendFunctions<SSAControlFlowGraph> {
        val ssa = compileToSSA(input)
        return FrontendCompilationFlow.optimizeSSA(ssa).also { ssaFfs ->
            ssaFfs.forEach { ssa ->
                testSourceMap(ssa.value)
            }
        }
    }

    fun compileToOptimizedCFG(input: String): FrontendFunctions<ControlFlowGraph> {
        val optimizedSSA = compileToOptimizedSSA(input)
        val ssaOnly = optimizedSSA.map { it.value }
        return FrontendCompilationFlow.convertFromSSA(ssaOnly).also { cfgFfs ->
            cfgFfs.forEach { cfg ->
                testSourceMap(cfg.value)
            }
        }
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

    // Checks that every function call has a source location in the source map
    private fun testSourceMap(cfg: ControlFlowGraph) {
        cfg.blocks.values.forEach { block ->
            block.irNodes.forEach { irNode ->
                if (irNode is IRFunctionCall) {
                    check(SourceLocationMap.get(cfg, irNode) != null) {
                        "Failed to find source location for function call ${irNode.printToString()}"
                    }
                }
            }
        }
    }
}