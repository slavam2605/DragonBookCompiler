package compiler.ir.optimization.clean

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.reachableFrom
import compiler.ir.cfg.utils.transformLabels

class RemoveUnreachableBlocks(private val cfg: ControlFlowGraph) {
    private val removedBlocks = mutableSetOf<IRLabel>()

    fun invoke(): ControlFlowGraph {
        buildRemovedBlocksList()
        return cfg.transformLabels(cfg.root, emptyList(), removedBlocks)
    }

    private fun buildRemovedBlocksList() {
        val reachable = cfg.reachableFrom(cfg.root)
        removedBlocks.addAll(cfg.blocks.keys.filter {
            it !in reachable
        })
    }
}