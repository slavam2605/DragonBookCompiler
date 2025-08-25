package compiler.ir.optimization.clean

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.transformLabels

class RemoveUnreachableBlocks(private val cfg: ControlFlowGraph) {
    private val removedBlocks = mutableSetOf<IRLabel>()

    fun invoke(): ControlFlowGraph {
        buildRemovedBlocksList()
        return cfg.transformLabels(cfg.root, emptyList(), removedBlocks)
    }

    private fun buildRemovedBlocksList() {
        val visited = mutableSetOf<IRLabel>()
        val stack = mutableListOf(cfg.root)
        while (stack.isNotEmpty()) {
            val label = stack.removeLast()
            if (label in visited) continue
            visited.add(label)
            cfg.edges(label).forEach { stack.add(it) }
        }

        removedBlocks.addAll(cfg.blocks.keys.filter {
            it !in visited
        })
    }
}