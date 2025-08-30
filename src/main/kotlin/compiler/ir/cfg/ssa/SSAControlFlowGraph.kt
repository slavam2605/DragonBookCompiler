package compiler.ir.cfg.ssa

import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRSource
import compiler.ir.IRVar
import compiler.ir.analysis.LiveVarAnalysis
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.DominanceFrontiers

/**
 * Special marker implementation of [compiler.ir.cfg.ControlFlowGraph] that guarantees that it is in SSA form.
 */
class SSAControlFlowGraph(root: IRLabel, blocks: Map<IRLabel, CFGBlock>) : ControlFlowGraph(root, blocks) {
    override fun new(root: IRLabel, blocks: Map<IRLabel, CFGBlock>): ControlFlowGraph {
        return SSAControlFlowGraph(root, blocks)
    }

    companion object {
        /**
         * Transforms the given [ControlFlowGraph] into an [SSAControlFlowGraph].
         */
        fun transform(cfg: ControlFlowGraph): SSAControlFlowGraph {
            val mutableBlocks = cfg.blocks.mapValues { (_, block) ->
                block.irNodes.toMutableList()
            }.toMutableMap()

            // Step 1. Compute "global" variables that are used in more than one block
            val (globals, blocksMap) = getGlobals(cfg)

            // Step 2. Insert phi nodes for the "global" variables using dominance frontiers
            insertPhiNodes(cfg, blocksMap, globals, mutableBlocks)

            // Step 3. Rename all variables to have unique names
            RenameVariablesForSSA(cfg, mutableBlocks).renameVariables()

            return SSAControlFlowGraph(
                cfg.root,
                mutableBlocks.mapValues { (_, irNodes) ->
                    CFGBlock(irNodes)
                }
            )
        }

        private fun insertPhiNodes(
            cfg: ControlFlowGraph,
            blocksMap: Map<IRVar, Set<IRLabel>>,
            globals: Set<IRVar>,
            mutableBlocks: MutableMap<IRLabel, MutableList<IRNode>>
        ) {
            val df = DominanceFrontiers.get(cfg)
            val liveIn = LiveVarAnalysis(cfg).run().inValues
            globals.forEach { globalVar ->
                // If `block in scheduled`, then `block` already has `x = phi(...)`
                // and `block` is in the work list now, or was already processed
                val scheduled = mutableSetOf<IRLabel>()

                val workList = blocksMap[globalVar]!!.toMutableList()
                while (workList.isNotEmpty()) {
                    val currentLabel = workList.removeLast()
                    df[currentLabel]!!.forEach { frontierLabel ->
                        if (frontierLabel in scheduled) {
                            return@forEach // `phi` already inserted, skip block
                        }
                        if (!liveIn[frontierLabel]!!.contains(globalVar)) {
                            return@forEach // Pruned SSA => no phi-nodes for dead variables
                        }

                        val phiSources = cfg.backEdges(frontierLabel).map { IRSource(it, globalVar) }
                        val phi = IRPhi(globalVar, phiSources)
                        mutableBlocks[frontierLabel]!!.add(0, phi)
                        scheduled.add(frontierLabel)
                        workList.add(frontierLabel)
                    }
                }
            }
        }

        private fun getGlobals(cfg: ControlFlowGraph): Pair<Set<IRVar>, Map<IRVar, Set<IRLabel>>> {
            // `hashSetOf` is used here instead of `mutableSetOf` for the only reason:
            // to make the `phi_dependency.txt` test meaningful. `mutableSetOf` created
            // a linked hash set, which retains the order of additions. `globals` map is created
            // in order of rvalues in blocks, which always places modified variables' phi-nodes
            // *after* they are used, making writing a phi-dependency test hard.
            // That's why `hashSetOf` is used here because it is ordered based on some
            // internal logic, such as hash code of `IRVar` instances.
            val globals = hashSetOf<IRVar>()

            val blocksMap = mutableMapOf<IRVar, MutableSet<IRLabel>>()
            cfg.blocks.forEach { (label, block) ->
                val varKill = mutableSetOf<IRVar>()
                block.irNodes.forEach { irNode ->
                    irNode.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                        if (rVar !in varKill) {
                            globals.add(rVar)
                        }
                    }
                    irNode.lvalue?.let { lVar ->
                        varKill.add(lVar)
                        blocksMap.getOrPut(lVar) { mutableSetOf() }.add(label)
                    }
                }
            }
            return globals to blocksMap
        }
    }
}