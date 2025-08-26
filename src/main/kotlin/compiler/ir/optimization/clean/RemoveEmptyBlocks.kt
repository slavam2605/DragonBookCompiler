package compiler.ir.optimization.clean

import compiler.ir.*
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.CFGEdgeChanged
import compiler.ir.cfg.utils.hasPhiNodes
import compiler.ir.cfg.utils.transformLabels

class RemoveEmptyBlocks(private val cfg: ControlFlowGraph) {
    data class IRTargetReplacement(val originalPredecessor: IRLabel, val newTarget: IRLabel)

    private val replacements = mutableMapOf<IRLabel, IRTargetReplacement>()
    private val changedEdges = mutableListOf<CFGEdgeChanged>()
    private val removedBlocks = mutableSetOf<IRLabel>()

    fun invoke(): ControlFlowGraph {
        findEmptyBlockReplacements()
        buildChangeEdgesList()
        val newRoot = replaceRootIfPossible()

        return cfg.transformLabels(newRoot, changedEdges, removedBlocks)
    }

    /**
     * Finds all blocks that contain only one instruction, that is an unconditional jump.
     * Builds `replacements` map that contains pairs of `(original predecessor, final target)`.
     *
     * Example:
     * ```
     *  L0: jump L2
     *  L1: jump L2
     *  L2: jump L3
     * ```
     * will return `[{L0 -> (L2, L3)}, {L1 -> (L2, L3)}, {L2 -> (L2, L3)}]`
     */
    private fun findEmptyBlockReplacements() {
        // Find all blocks that contain only a jump instruction
        cfg.blocks.forEach { (label, block) ->
            if (block.irNodes.size == 1 && block.irNodes[0] is IRJump) {
                val jump = block.irNodes[0] as IRJump
                replacements[label] = IRTargetReplacement(label, jump.target)
            }
        }

        // Follow the chain of replacements to find the final non-empty block
        val keys = replacements.keys.toList()
        for (label in keys) {
            replacements[label] = findFinalReplacement(label)
        }
    }

    private fun findFinalReplacement(label: IRLabel): IRTargetReplacement {
        var current = label
        var currentReplacement: IRTargetReplacement? = null
        val visited = mutableSetOf<IRLabel>()

        while (current in replacements && current !in visited) {
            visited.add(current)
            currentReplacement = replacements[current]!!
            current = currentReplacement.newTarget
        }

        return currentReplacement!!
    }

    private fun buildChangeEdgesList() {
        cfg.blocks.forEach { (blockLabel, block) ->
            val takenTargets = mutableSetOf<IRLabel>()
            block.irNodes
                .filterIsInstance<IRJumpNode>()
                .forEach { node ->
                    node.labels().forEach { target ->
                        if (target !in replacements) {
                            // Initialize targets that will not be replaced
                            // Otherwise, if a replacement candidate goes before such a target,
                            // it will be replaced and lead to phi source duplicates
                            takenTargets.add(target)
                        }
                    }
                }

            block.irNodes.filterIsInstance<IRJumpNode>().forEach { jump ->
                for (oldTarget in jump.labels()) {
                    val replacement = replacements[oldTarget] ?: continue
                    if (replacement.newTarget in takenTargets && replacement.newTarget.hasPhiNodes(cfg)) {
                        // Do not replace more than one label to the same target (if the target has phi nodes)
                        // Otherwise, phi nodes in the target block will have duplicated source blocks
                        continue
                    }

                    takenTargets.add(replacement.newTarget)
                    changedEdges.add(
                        CFGEdgeChanged(
                            blockLabel,
                            oldTarget,
                            replacement.newTarget,
                            replacement.originalPredecessor
                        )
                    )
                }
            }
        }
    }

    /**
     * Attempts to replace the CFG root. It is possible only if the root is empty,
     * contains only a single jump instruction, and the target block doesn't have any phi nodes.
     */
    private fun replaceRootIfPossible(): IRLabel {
        val rootReplacement = replacements[cfg.root]
            ?: return cfg.root

        val backEdgeCount = cfg.blocks
            .flatMap {
                it.value.irNodes
                    .filterIsInstance<IRJumpNode>()
                    .flatMap { jump -> jump.labels() }
            }
            .map { replacements[it]?.newTarget ?: it }
            .count { it == rootReplacement.newTarget }
        if (backEdgeCount > 1) {
            return cfg.root
        }

        val allTrivialPhiNodes = cfg.blocks[rootReplacement.newTarget]!!.irNodes
            .filterIsInstance<IRPhi>()
            .all {
                it.sources.size == 1 &&
                it.sources[0].from == rootReplacement.originalPredecessor
            }
        if (!allTrivialPhiNodes) {
            // To replace the root, all phi-nodes in the replacement
            // must look like `x = phi(<root>: y)`, so it can be replaced
            // by `x = y` in `CFGTransformationUtilsKt.fixPhiNodes`
            return cfg.root
        }

        removedBlocks.add(cfg.root)
        return rootReplacement.newTarget
    }
}