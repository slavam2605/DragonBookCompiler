package compiler.ir.cfg

import compiler.ir.IRLabel

object DominatorTree {
    private val Key = ExtensionKey<Map<IRLabel, IRLabel>>("DominatorTree")

    /**
     * Returns a map from labels to their immediate dominator.
     * If fact, this map forms back edges for the dominator tree.
     */
    fun get(cfg: ControlFlowGraph): Map<IRLabel, IRLabel> {
        return cfg.getOrCompute(Key) { compute(cfg) }
    }

    private fun compute(cfg: ControlFlowGraph): Map<IRLabel, IRLabel> {
        val iDom = mutableMapOf<IRLabel, IRLabel>()
        val dom = CFGDominance.get(cfg)
        dom.forEach { (label, dominators) ->
            val strictDominators = dominators - label
            if (strictDominators.isEmpty()) {
                // root or an unreachable subgraph's root
                return@forEach
            }

            loop@ for (candidate in strictDominators) {
                val candidateDom = dom[candidate]!!
                strictDominators.forEach { other ->
                    if (other == candidate) return@forEach
                    if (!candidateDom.contains(other)) {
                        continue@loop
                    }
                }

                iDom[label] = candidate
                break@loop
            }

            check(iDom[label] != null) {
                "Failed to find immediate dominator for $label"
            }
        }
        return iDom
    }
}