package compiler.ir.cfg.utils

import compiler.ir.*
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.cfg.transformLabel
import compiler.ir.printToString
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.set

data class CFGEdgeChanged(
    val fromLabel: IRLabel,
    val oldTarget: IRLabel,
    val newTarget: IRLabel,
    val originalPredecessor: IRLabel
)

private typealias FromLabel = IRLabel
private typealias OldTarget = IRLabel
private typealias NewTarget = IRLabel
private typealias OriginalPredecessor = IRLabel

fun ControlFlowGraph.transformLabels(
    newRoot: IRLabel,
    changedEdges: List<CFGEdgeChanged>,
    removedBlocks: Set<IRLabel>
): ControlFlowGraph {
    if (changedEdges.isEmpty() && removedBlocks.isEmpty()) {
        // Return the same instance to mark that nothing changed
        return this
    }

    val (blocks, sourceMap) = transformLabels(this, newRoot, changedEdges, removedBlocks)
    return new(newRoot, blocks).apply {
        SourceLocationMap.storeMap(sourceMap, this)
    }
}

private fun transformLabels(
    cfg: ControlFlowGraph,
    newRoot: IRLabel,
    changedEdges: List<CFGEdgeChanged>,
    removedBlocks: Set<IRLabel>
): Pair<Map<IRLabel, CFGBlock>, SourceLocationMap> {
    val removed = removedBlocks.toSet()
    val changedByFromOld = mutableMapOf<FromLabel, MutableMap<OldTarget, CFGEdgeChanged>>()
    val changedByPredNew = mutableMapOf<Pair<OriginalPredecessor, NewTarget>, MutableList<CFGEdgeChanged>>()
    changedEdges.forEach { edge ->
        changedByFromOld.getOrPut(edge.fromLabel) { mutableMapOf() }[edge.oldTarget] = edge
        changedByPredNew.getOrPut(edge.originalPredecessor to edge.newTarget) { mutableListOf() }.add(edge)
    }

    val sourceMap = SourceLocationMap.extractMap(cfg) ?: SourceLocationMap.empty()
    val newBlocks = cfg.blocks.toMutableMap()

    applyChangedEdges(sourceMap, newBlocks, changedByFromOld)
    applyRemovedBlocks(newBlocks, removed)
    if (cfg is SSAControlFlowGraph) {
        fixPhiNodes(newRoot, newBlocks, changedByPredNew)
    }

    return newBlocks to sourceMap
}

private fun applyChangedEdges(
    sourceMap: SourceLocationMap,
    newBlocks: MutableMap<IRLabel, CFGBlock>,
    changed: Map<FromLabel, Map<OldTarget, CFGEdgeChanged>>
) {
    for ((blockLabel, block) in newBlocks) {
        val changedForBlock = changed[blockLabel]
            ?: continue

        newBlocks[blockLabel] = block.transformLabel(sourceMap) { oldTarget ->
            changedForBlock[oldTarget]?.newTarget ?: oldTarget
        }
    }
}

private fun applyRemovedBlocks(
    newBlocks: MutableMap<IRLabel, CFGBlock>,
    removed: Set<IRLabel>
) {
    newBlocks.keys.removeAll(removed)

    // Verify that jump nodes do not reference removed blocks
    newBlocks.forEach { (label, block) ->
        block.irNodes.filterIsInstance<IRJumpNode>().forEach { jump ->
            jump.labels().forEach { target ->
                check(target !in removed) {
                    "Jump node ${jump.printToString()} in block ${label.printToString()} " +
                            "references removed block ${target.printToString()}"
                }
            }
        }
    }
}

private fun fixPhiNodes(
    newRoot: IRLabel,
    newBlocks: MutableMap<IRLabel, CFGBlock>,
    changed: Map<Pair<OriginalPredecessor, NewTarget>, List<CFGEdgeChanged>>
) {
    newBlocks.forEach { (label, block) ->
        newBlocks[label] = block.transform(
            PhiTransformer(label, newRoot, newBlocks, changed)
        )
    }
}

private class PhiTransformer(
    private val currentBlock: IRLabel,
    private val newRoot: IRLabel,
    private val newBlocks: Map<IRLabel, CFGBlock>,
    private val changed: Map<Pair<OriginalPredecessor, NewTarget>, List<CFGEdgeChanged>>
) : BaseIRTransformer() {
    override fun transformNode(node: IRNode): IRNode {
        if (node !is IRPhi) {
            return node
        }

        val newSources = mutableListOf<IRSource>()
        node.sources.forEach { (from, value) ->
            val replacements = changed[from to currentBlock]?.map { it.fromLabel }
                ?: listOf(from)

            // Add `phi(new_from: x)` as one replacement for `phi(from: x)`
            // Result will be `phi(new_from_1: x, new_from_2: x, ...)`, because many (or none)
            // blocks may have been replaced with `jump <this-node>`
            for (newFrom in replacements) {
                // Do not add sources from removed blocks
                if (newFrom !in newBlocks.keys) continue
                newSources.add(IRSource(newFrom, value))
            }
        }

        check(newSources.distinctBy { it.from }.size == newSources.size) {
            "Phi node sources have duplicate 'from' blocks: ${IRPhi(node.result, newSources).printToString()}"
        }
        return when (newSources.size) {
            0 -> {
                if (currentBlock == newRoot && node.sources.size == 1) {
                    // TODO move assign *after* all phi nodes
                    return IRAssign(node.result, node.sources.single().value)
                }
                error("Phi node ${node.printToString()} now has no sources")
            }
            // TODO return IRAssign if `newSources.size == 1`
            //  - must be moved after all phi blocks
            //  - all phi nodes are assigned simultaneously, check that converting to assign is safe
            else -> IRPhi(node.result, newSources)
        }
    }
}