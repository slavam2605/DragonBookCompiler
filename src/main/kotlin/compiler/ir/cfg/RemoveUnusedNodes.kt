package compiler.ir.cfg

import compiler.ir.BaseIRTransformer
import compiler.ir.IRJump
import compiler.ir.IRLabel

object RemoveUnusedNodes {
    fun invoke(cfg: ControlFlowGraph): ControlFlowGraph {
        val replacements = findEmptyBlockReplacements(cfg)
        val newRoot = replacements[cfg.root] ?: cfg.root
        val transformer = object : BaseIRTransformer() {
            override fun transformLabel(label: IRLabel): IRLabel {
                return replacements[label] ?: label
            }
        }
        val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
        cfg.blocks.forEach { (label, block) ->
            if (label in replacements) return@forEach
            newBlocks[label] = block.transform(transformer)
        }
        removeUnusedBlocks(newRoot, newBlocks)

        return ControlFlowGraph(newRoot, newBlocks)
    }
    
    // Remove all unused blocks
    private fun removeUnusedBlocks(root: IRLabel, blocks: MutableMap<IRLabel, CFGBlock>) {
        val usedBlocks = blocks.values.flatMap { block ->
            block.irNodes.flatMap { it.labels() }
        }.toSet()
        blocks.keys.removeIf { it != root && it !in usedBlocks }
    }
    
    // Find replacements for empty blocks (blocks with only a jump instruction)
    private fun findEmptyBlockReplacements(cfg: ControlFlowGraph): Map<IRLabel, IRLabel> {
        val replacements = mutableMapOf<IRLabel, IRLabel>()
        
        // Find all blocks that contain only a jump instruction
        cfg.blocks.forEach { (label, block) ->
            if (block.irNodes.size == 1 && block.irNodes[0] is IRJump) {
                val jump = block.irNodes[0] as IRJump
                replacements[label] = jump.target
            }
        }

        // Follow the chain of replacements to find the final non-empty block
        val keys = replacements.keys.toList()
        for (label in keys) {
            replacements[label] = findFinalReplacement(label, replacements)
        }

        return replacements
    }
    
    // Follow the chain of replacements to find the final non-empty block
    private fun findFinalReplacement(label: IRLabel, replacements: Map<IRLabel, IRLabel>): IRLabel {
        var current = label
        val visited = mutableSetOf<IRLabel>()
        
        while (current in replacements && current !in visited) {
            visited.add(current)
            current = replacements[current]!!
        }
        
        return current
    }
}