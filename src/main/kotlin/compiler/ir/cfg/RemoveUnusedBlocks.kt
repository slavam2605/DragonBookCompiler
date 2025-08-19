package compiler.ir.cfg

import compiler.ir.BaseIRTransformer
import compiler.ir.IRJump
import compiler.ir.IRJumpNode
import compiler.ir.IRLabel
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.cfg.ssa.SSARemoveUnusedBlocks

open class RemoveUnusedBlocks(protected val cfg: ControlFlowGraph) {
    data class IRTargetReplacement(val originalPredecessor: IRLabel, val newTarget: IRLabel)

    protected val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
    protected val replacements = mutableMapOf<IRLabel, IRTargetReplacement>()
    protected val usedReplacements = mutableListOf<Pair<IRLabel, IRTargetReplacement>>()
    private val sourceMap = SourceLocationMap.extractMap(cfg) ?: SourceLocationMap.empty()

    protected open fun transformPhiNodes() {
        // No phi nodes can be in non-SSA CFG, do nothing
    }

    open fun invoke(): ControlFlowGraph {
        check(cfg !is SSAControlFlowGraph) {
            "Use ${SSARemoveUnusedBlocks::class.simpleName} for SSA control-flow graphs"
        }
        val newRoot = doInvoke()
        return ControlFlowGraph(newRoot, newBlocks).apply {
            SourceLocationMap.storeMap(sourceMap, this)
        }
    }

    protected fun doInvoke(): IRLabel {
        findEmptyBlockReplacements()
        applyLabelReplacements()
        removeUnusedBlocks()
        transformPhiNodes()
        return replaceRootIfNeeded()
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
    private fun findEmptyBlockReplacements(): Map<IRLabel, IRTargetReplacement> {
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

        // Exclude root from replacements, it will be replaced in `replaceRootIfNeeded`, if possible
        replacements.remove(cfg.root)

        return replacements
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

    /**
     * Builds `newBlocks` map, transforming all labels according to `replacements` map.
     * Also, collects replacement usages in `usedReplacements` list, to be used in `transformPhiNodes` later.
     */
    private fun applyLabelReplacements() {
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

            val transformer = object : BaseIRTransformer() {
                override fun transformLabel(label: IRLabel): IRLabel {
                    val replacement = replacements[label] ?: return label
                    if (replacement.newTarget in takenTargets) {
                        // Do not replace more than one label to the same target
                        // Otherwise, phi nodes in the target block will have duplicated source blocks
                        return label
                    }

                    takenTargets.add(replacement.newTarget)
                    usedReplacements.add(blockLabel to replacement)
                    return replacement.newTarget
                }
            }
            newBlocks[blockLabel] = block.transform(transformer, sourceMap)
        }
    }

    /**
     * Removes all unused blocks, if they were unused initially or after applying label replacements.
     */
    private fun removeUnusedBlocks() {
        val usedBlocks = newBlocks.values.flatMap { block ->
            block.irNodes
                .filterIsInstance<IRJumpNode>()
                .flatMap { it.labels() }
        }.toSet()
        newBlocks.keys.removeIf { it != cfg.root && it !in usedBlocks }
    }

    /**
     * Attempts to replace the CFG root. It is possible only if the root is empty,
     * contains only a single jump instruction, and the target block doesn't have any back edges.
     */
    private fun replaceRootIfNeeded(): IRLabel {
        val rootBlock = newBlocks[cfg.root]!!
        val singleJump = rootBlock.irNodes.singleOrNull() as? IRJump
            ?: return cfg.root

        val hasOtherBackEdges = newBlocks.any { (from, block) ->
            from != cfg.root && singleJump.target in block.irNodes.flatMap {
                (it as? IRJumpNode)?.labels() ?: emptyList()
            }
        }
        if (hasOtherBackEdges) {
            // Root must not have any back edges, must keep the current root
            return cfg.root
        }

        // Remove the old root block and return the new target label
        newBlocks.remove(cfg.root)
        return singleJump.target
    }
}