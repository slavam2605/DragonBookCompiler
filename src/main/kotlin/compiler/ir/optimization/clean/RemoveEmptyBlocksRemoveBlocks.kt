package compiler.ir.optimization.clean

import compiler.ir.IRJumpNode
import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.transformLabels

class RemoveEmptyBlocksRemoveBlocks(private val cfg: ControlFlowGraph) {
    private val removedBlocks = mutableSetOf<IRLabel>()

    fun invoke(): ControlFlowGraph {
        buildRemovedBlocksList()
        return cfg.transformLabels(cfg.root, emptyList(), removedBlocks)
    }

    private fun buildRemovedBlocksList() {
        val usedBlocks = cfg.blocks.values.flatMap { block ->
            block.irNodes
                .filterIsInstance<IRJumpNode>()
                .flatMap { it.labels() }
        }.toSet()
        removedBlocks.addAll(cfg.blocks.keys.filter {
            it != cfg.root && it !in usedBlocks
        })
    }
}