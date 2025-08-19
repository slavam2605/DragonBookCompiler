package compiler.ir.cfg.ssa

import compiler.ir.BaseIRTransformer
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRSource
import compiler.ir.cfg.RemoveUnusedBlocks
import compiler.ir.printToString
import kotlin.collections.forEach
import kotlin.collections.ifEmpty

class SSARemoveUnusedBlocks(cfg: SSAControlFlowGraph) : RemoveUnusedBlocks(cfg) {
    override fun invoke(): SSAControlFlowGraph {
        val newRoot = doInvoke()
        return SSAControlFlowGraph(newRoot, newBlocks)
    }

    /**
     * Fixes phi-nodes after the label replacement.
     *
     * It uses `usedReplacements`, collected from the label replacement,
     * to replace old phi-node sources with the new ones. Each source may be replaced
     * with zero new sources (if the original predecessor was unused),
     * with one new source, or with more new sources (if several blocks
     * jumped to the same predecessor).
     *
     * Example:
     * ```
     *  L0: jump L2
     *  L1: jump L2
     *  L2: jump L3
     *  L3: x = phi(L2: y)
     * ```
     * will be transformed to:
     * ```
     *  L0: jump L3
     *  L1: jump L3
     *  L3: x = phi(L0: y, L1: y)
     * ```
     */
    override fun transformPhiNodes() {
        newBlocks.forEach { (label, block) ->
            newBlocks[label] = block.transform(PhiTransformer(label))
        }
    }

    private inner class PhiTransformer(private val currentBlock: IRLabel) : BaseIRTransformer() {
        override fun transformNode(node: IRNode): IRNode {
            if (node !is IRPhi) {
                return node
            }

            val newSources = mutableListOf<IRSource>()
            node.sources.forEach { (from, value) ->
                val replacements = usedReplacements.mapNotNull { (usedLabel, replacement) ->
                    // Find all blocks where `jump L` was replaced with `jump <this-node>`,
                    // and the final predecessor of `<this-node>` was `from`
                    if (replacement.originalPredecessor == from) {
                        check(replacement.newTarget == currentBlock)
                        usedLabel
                    } else null
                }.ifEmpty {
                    // If no replacement was used, keep the original `from` block
                    listOf(from)
                }

                // Add `phi(new_from: x)` as one replacement for `phi(from: x)`
                // Result will be `phi(new_from_1: x, new_from_2: x, ...)`, because many (or none)
                // blocks may have been replaced with `jump <this-node>`
                replacements.forEach { newFrom ->
                    newSources.add(IRSource(newFrom, value))
                }
            }
            // Remove all blocks missing from `newBlocks`, they are unused
            newSources.removeIf { it.from !in newBlocks.keys }

            check(newSources.distinctBy { it.from }.size == newSources.size) {
                "Phi node sources have duplicate 'from' blocks: ${IRPhi(node.result, newSources).printToString()}"
            }
            return when (newSources.size) {
                0 -> error("Phi node ${node.printToString()} now has no sources")
                // TODO return IRAssign if `newSources.size == 1`
                //  - must be moved after all phi blocks
                //  - all phi nodes are assigned simultaneously, check that converting to assign is safe
                else -> IRPhi(node.result, newSources)
            }
        }
    }
}