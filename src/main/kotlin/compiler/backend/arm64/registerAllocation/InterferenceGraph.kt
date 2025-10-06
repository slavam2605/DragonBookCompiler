package compiler.backend.arm64.registerAllocation

import compiler.ir.IRAssign
import compiler.ir.IRFunctionCall
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

/**
 * Result of register allocation analysis, containing interference graph and liveness information.
 */
data class AllocationAnalysisResult(
    val graph: InterferenceGraph,
    val livenessInfo: LivenessInfo
)

class InterferenceGraph private constructor(cfg: ControlFlowGraph) {
    private val mutableEdges = mutableMapOf<IRVar, MutableSet<IRVar>>()

    val edges: Map<IRVar, Set<IRVar>> = mutableEdges

    init {
        // Initialize vertices for all variables (type-agnostic)
        cfg.blocks.forEach { (_, block) ->
            block.irNodes.forEach { node ->
                node.lvalue?.let { lVar ->
                    mutableEdges.putIfAbsent(lVar, mutableSetOf())
                }
                node.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                    mutableEdges.putIfAbsent(rVar, mutableSetOf())
                }
            }
        }
    }

    private fun addEdge(from: IRVar, to: IRVar) {
        if (from == to) return
        mutableEdges[from]!!.add(to)
        mutableEdges[to]!!.add(from)
    }

    companion object {
        /**
         * Builds a type-agnostic interference graph and collects liveness information in a single pass.
         * The graph contains all variables, but edges are only added between variables of the same type.
         */
        fun create(cfg: ControlFlowGraph): AllocationAnalysisResult {
            val graph = InterferenceGraph(cfg)
            val liveAtCallsMap = mutableMapOf<IRFunctionCall, Set<IRVar>>()
            val liveAfterBranchMap = mutableMapOf<IRJumpIfTrue, Set<IRVar>>()

            PerNodeLiveVarAnalysis(cfg).run { irNode, liveIn, liveOut ->
                // Collect liveness at call sites (type-agnostic)
                if (irNode is IRFunctionCall) {
                    liveAtCallsMap[irNode] = liveIn.intersect(liveOut)
                }
                if (irNode is IRJumpIfTrue) {
                    liveAfterBranchMap[irNode] = liveOut
                }

                // Build interference edges (same-type only)
                irNode.lvalue?.let { lVar ->
                    // Filter live variables to same type as lVar
                    val sameLiveOut = liveOut.filter { it.type == lVar.type }

                    // Special handling for copy instructions: x = y
                    // x interferes with everything live EXCEPT y
                    // This allows x and y to be coalesced (get the same register)
                    if (irNode is IRAssign && irNode.right is IRVar) {
                        val copySource = irNode.right
                        sameLiveOut
                            .filter { it != copySource }  // Exclude the copy source from interference
                            .forEach { graph.addEdge(it, lVar) }
                    } else {
                        // Non-copy: lhs interferes with everything live of the same type
                        sameLiveOut.forEach { graph.addEdge(it, lVar) }
                    }
                }
            }

            val livenessInfo = LivenessInfo(liveAtCallsMap, liveAfterBranchMap)
            return AllocationAnalysisResult(graph, livenessInfo)
        }
    }
}