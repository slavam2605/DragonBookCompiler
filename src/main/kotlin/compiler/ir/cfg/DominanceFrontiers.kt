package compiler.ir.cfg

import compiler.ir.IRLabel

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
                while (runner != iDom[label]) {
                    frontiers[runner]!!.add(label)
                    runner = iDom[runner]
                }
            }
        }
        return frontiers
    }
}