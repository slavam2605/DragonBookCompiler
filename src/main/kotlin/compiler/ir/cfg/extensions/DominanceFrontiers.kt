package compiler.ir.cfg.extensions

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey
import compiler.ir.cfg.utils.reachableFrom

object DominanceFrontiers {
    private val Key = ExtensionKey<Map<IRLabel, Set<IRLabel>>>("DominanceFrontiers")

    fun get(cfg: ControlFlowGraph): Map<IRLabel, Set<IRLabel>> {
        return cfg.getOrCompute(Key) { compute(cfg) }
    }

    private fun compute(cfg: ControlFlowGraph): Map<IRLabel, Set<IRLabel>> {
        val frontiers = mutableMapOf<IRLabel, MutableSet<IRLabel>>()
        cfg.blocks.keys.forEach { label ->
            frontiers[label] = mutableSetOf()
        }

        val iDom = DominatorTree.get(cfg)
        cfg.blocks.keys.forEach { label ->
            val predecessors = cfg.backEdges(label)
            if (predecessors.size < 2) return@forEach
            predecessors.forEach { pred ->
                var runner: IRLabel? = pred
                while (runner != null && runner != iDom.iDom(label)) {
                    frontiers[runner]!!.add(label)
                    runner = iDom.iDom(runner)
                }
            }
        }

        val reachable = cfg.reachableFrom(cfg.root)
        return frontiers.filterKeys { it in reachable }
    }
}