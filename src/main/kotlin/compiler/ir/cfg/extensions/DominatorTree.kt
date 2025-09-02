package compiler.ir.cfg.extensions

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey
import compiler.ir.cfg.utils.reachableFrom
import kotlin.collections.get

class DominatorTree(
    private val edges: Map<IRLabel, Set<IRLabel>>,
    private val backEdges: Map<IRLabel, IRLabel>
) {
    fun edges(label: IRLabel): Set<IRLabel> = edges[label] ?: emptySet()

    /**
     * Returns the immediate dominator of the given block.
     */
    fun iDom(label: IRLabel?): IRLabel? = backEdges[label]

    companion object {
        private val Key = ExtensionKey<DominatorTree>("DominatorTree")

        /**
         * Returns a map from labels to their immediate dominator.
         * If fact, this map forms back edges for the dominator tree.
         */
        fun get(cfg: ControlFlowGraph): DominatorTree {
            return cfg.getOrCompute(Key) { compute(cfg) }
        }

        private fun compute(cfg: ControlFlowGraph): DominatorTree {
            val iDom = mutableMapOf<IRLabel, IRLabel>()
            val dom = CFGDominance.get(cfg)
            val reachable = cfg.reachableFrom(cfg.root)
            dom.forEach { (label, dominators) ->
                val strictDominators = dominators - label
                if (label !in reachable || strictDominators.isEmpty()) {
                    // cfg's entry (root) or unreachable block
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

            val edges = cfg.blocks.mapValues { (label, _) ->
                cfg.blocks.keys.filter { iDom[it] == label }.toSet()
            }
            return DominatorTree(edges, iDom)
        }
    }
}