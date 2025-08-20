package compiler.ir.cfg.extensions

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey
import compiler.ir.cfg.ReversedPostOrderTraversal

object CFGDominance {
    private val Key = ExtensionKey<Map<IRLabel, Set<IRLabel>>>("DominanceSets")

    fun get(cfg: ControlFlowGraph): Map<IRLabel, Set<IRLabel>> {
        return cfg.getOrCompute(Key) { compute(cfg) }
    }

    private fun compute(cfg: ControlFlowGraph): Map<IRLabel, Set<IRLabel>> {
        val dom = mutableMapOf<IRLabel, Set<IRLabel>>()
        dom[cfg.root] = mutableSetOf(cfg.root)
        cfg.blocks.keys.forEach { label ->
            if (label == cfg.root) return@forEach
            dom[label] = cfg.blocks.keys.toSet()
        }

        var changed = true
        while (changed) {
            changed = false
            ReversedPostOrderTraversal.traverse(cfg) { label, _ ->
                val backEdges = cfg.backEdges(label)
                val intersection = if (backEdges.isEmpty()) {
                    emptySet()
                } else {
                    backEdges.map { dom[it]!! }
                        .reduce { acc, set -> acc.intersect(set) }
                }
                val temp = setOf(label) + intersection
                if (temp != dom[label]) {
                    changed = true
                    dom[label] = temp
                }
            }
        }

        return dom
    }
}