package compiler.backend.arm64.registerAllocation

import compiler.ir.IRAssign
import compiler.ir.IRFunctionCall
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

/**
 * Result of building an interference graph and collecting liveness information.
 */
data class InterferenceGraphResult(
    val graph: InterferenceGraph,
    val livenessInfo: LivenessInfo
)

class InterferenceGraph private constructor(cfg: ControlFlowGraph, filter: (IRVar) -> Boolean) {
    private val mutableEdges = mutableMapOf<IRVar, MutableSet<IRVar>>()

    val edges: Map<IRVar, Set<IRVar>> = mutableEdges

    init {
        cfg.blocks.forEach { (_, block) ->
            block.irNodes.forEach { node ->
                node.lvalue?.let { lVar ->
                    if (!filter(lVar)) return@let
                    mutableEdges.putIfAbsent(lVar, mutableSetOf())
                }
                node.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                    if (!filter(rVar)) return@forEach
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
         * Builds interference graph and collects liveness information in a single pass.
         * The filter parameter is only applied to interference graph construction, not to liveness info.
         */
        fun create(cfg: ControlFlowGraph, filter: (IRVar) -> Boolean): InterferenceGraphResult {
            val graph = InterferenceGraph(cfg, filter)
            val liveAtCallsMap = mutableMapOf<IRFunctionCall, Set<IRVar>>()

            PerNodeLiveVarAnalysis(cfg).run { irNode, liveIn, liveOut ->
                // Collect liveness at call sites (type-agnostic)
                if (irNode is IRFunctionCall) {
                    liveAtCallsMap[irNode] = liveIn.intersect(liveOut)
                }

                // Build interference edges (type-specific via filter)
                irNode.lvalue?.let { lVar ->
                    if (!filter(lVar)) return@run

                    // Special handling for copy instructions: x = y
                    // x interferes with everything live EXCEPT y
                    // This allows x and y to be coalesced (get the same register)
                    if (irNode is IRAssign && irNode.right is IRVar) {
                        val copySource = irNode.right
                        liveOut
                            .filter(filter)
                            .filter { it != copySource }  // Exclude the copy source from interference
                            .forEach { graph.addEdge(it, lVar) }
                    } else {
                        // Non-copy: lhs interferes with everything live
                        liveOut
                            .filter(filter)
                            .forEach { graph.addEdge(it, lVar) }
                    }
                }
            }

            val livenessInfo = LivenessInfo(liveAtCallsMap)
            return InterferenceGraphResult(graph, livenessInfo)
        }
    }
}