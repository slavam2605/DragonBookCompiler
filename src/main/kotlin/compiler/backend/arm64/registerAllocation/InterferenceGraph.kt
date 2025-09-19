package compiler.backend.arm64.registerAllocation

import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class InterferenceGraph private constructor(cfg: ControlFlowGraph) {
    private val mutableEdges = mutableMapOf<IRVar, MutableSet<IRVar>>()

    val edges: Map<IRVar, Set<IRVar>> = mutableEdges

    init {
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
        fun create(cfg: ControlFlowGraph, filter: (IRVar) -> Boolean): InterferenceGraph {
            val graph = InterferenceGraph(cfg)
            PerNodeLiveVarAnalysis(cfg).run { irNode, _, liveOut ->
                irNode.lvalue?.let { lVar ->
                    if (!filter(lVar)) return@run
                    liveOut
                        .filter(filter)
                        .forEach { graph.addEdge(it, lVar) }
                }
            }
            return graph
        }
    }
}