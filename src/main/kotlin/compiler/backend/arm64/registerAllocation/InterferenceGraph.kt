package compiler.backend.arm64.registerAllocation

import compiler.ir.IRAssign
import compiler.ir.IRFunctionCall
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class InterferenceGraph private constructor(cfg: ControlFlowGraph, filter: (IRVar) -> Boolean) {
    private val mutableEdges = mutableMapOf<IRVar, MutableSet<IRVar>>()
    private val mutableLiveAtCalls = mutableMapOf<IRFunctionCall, Set<IRVar>>()

    val edges: Map<IRVar, Set<IRVar>> = mutableEdges

    /**
     * Maps each function call to the set of variables whose live ranges span the call.
     * These are variables in the intersection of liveIn and liveOut at the call site.
     */
    val liveAtCalls: Map<IRFunctionCall, Set<IRVar>> = mutableLiveAtCalls

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
        fun create(cfg: ControlFlowGraph, filter: (IRVar) -> Boolean): InterferenceGraph {
            val graph = InterferenceGraph(cfg, filter)
            PerNodeLiveVarAnalysis(cfg).run { irNode, liveIn, liveOut ->
                // Collect liveness at call sites
                if (irNode is IRFunctionCall) {
                    graph.mutableLiveAtCalls[irNode] = liveIn.intersect(liveOut)
                }

                // Build interference edges
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
            return graph
        }
    }
}