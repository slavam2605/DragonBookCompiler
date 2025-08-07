package compiler.ir.cfg

import compiler.ir.*

/**
 * Special marker implementation of [ControlFlowGraph] that guarantees that it is in SSA form.
 */
class SSAControlFlowGraph(root: IRLabel, blocks: Map<IRLabel, CFGBlock>) : ControlFlowGraph(root, blocks) {
    companion object {
        /**
         * Transforms the given [ControlFlowGraph] into an [SSAControlFlowGraph].
         */
        fun transform(cfg: ControlFlowGraph): SSAControlFlowGraph {
            val mutableBlocks = cfg.blocks.mapValues { (_, block) ->
                block.irNodes.toMutableList()
            }.toMutableMap()

            val (globals, blocksMap) = getGlobals(cfg)
            insertPhiNodes(cfg, blocksMap, globals, mutableBlocks)
            renameVariables(cfg, blocksMap.keys, mutableBlocks)

            return SSAControlFlowGraph(
                cfg.root,
                mutableBlocks.mapValues { (_, irNodes) ->
                    CFGBlock(irNodes)
                }
            )
        }

        private fun renameVariables(
            cfg: ControlFlowGraph,
            allVars: Set<IRVar>,
            mutableBlocks: MutableMap<IRLabel, MutableList<IRNode>>
        ) {
            val counter = mutableMapOf<String, Int>()
            val stack = mutableMapOf<String, MutableList<Int>>()
            allVars.forEach {
                counter[it.name] = 0
                stack[it.name] = mutableListOf()
            }

            fun newName(x: IRVar): IRVar {
                val index = counter[x.name]!!
                counter[x.name] = index + 1
                stack[x.name]!!.add(index)
                return IRVar(x.name, index)
            }

            val dominatorTree = DominatorTree.get(cfg)
            fun renameBlock(label: IRLabel) {
                // Step 1. Rename all variables in the block
                val block = mutableBlocks[label]!!
                var isInPhiPrefix = true
                block.forEachIndexed { index, node ->
                    if (node !is IRPhi) isInPhiPrefix = false
                    check(isInPhiPrefix || node !is IRPhi)

                    if (node is IRPhi) {
                        block[index] = IRPhi(newName(node.result), node.sources)
                    } else {
                        val renamedVars = node.rvalues()
                            .filterIsInstance<IRVar>()
                            .associateWith {
                                val ssaIndex = stack[it.name]!!.last()
                                IRVar(it.name, ssaIndex)
                            }
                        val lVars = node.lvalues()
                        check(lVars.size <= 2 || lVars.distinct().size == lVars.size)
                        val newLVars = lVars.associateWith { newName(it) }
                        block[index] = node.transform(object : BaseIRTransformer() {
                            override fun transformLValue(value: IRVar) = newLVars[value]!!
                            override fun transformRValue(value: IRValue) = renamedVars[value] ?: value
                        })
                    }
                }

                // Step 2. Fill in phi-nodes in **successor blocks of CFG**
                cfg.edges(label).forEach { successor ->
                    val nextBlock = mutableBlocks[successor]!!
                    val selfIndex = cfg.getBlockIndex(label, successor)
                    nextBlock.forEachIndexed { index, node ->
                        if (node !is IRPhi) return@forEachIndexed
                        val sourceVar = node.sources[selfIndex]
                        val lastSSAVer = stack[sourceVar.name]!!.last()
                        nextBlock[index] = node.replaceSourceAt(selfIndex, IRVar(sourceVar.name, lastSSAVer))
                    }
                }

                // Step 3. Recursively handle all **successor blocks in the dominator tree**
                dominatorTree.edges(label).forEach { successor ->
                    renameBlock(successor)
                }

                // Step 4. Unwind stacks modified in this block
                block.forEach { node ->
                    node.lvalues().forEach { lVar ->
                        stack[lVar.name]!!.removeLast()
                    }
                }
            }

            renameBlock(cfg.root)
        }

        private fun insertPhiNodes(
            cfg: ControlFlowGraph,
            blocksMap: Map<IRVar, Set<IRLabel>>,
            globals: Set<IRVar>,
            mutableBlocks: MutableMap<IRLabel, MutableList<IRNode>>
        ) {
            val df = DominanceFrontiers.get(cfg)
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

                        val predCount = cfg.backEdges(frontierLabel).size
                        val phi = IRPhi(globalVar, List(predCount) { globalVar })
                        mutableBlocks[frontierLabel]!!.add(0, phi)
                        scheduled.add(frontierLabel)
                        workList.add(frontierLabel)
                    }
                }
            }
        }

        private fun getGlobals(cfg: ControlFlowGraph): Pair<Set<IRVar>, Map<IRVar, Set<IRLabel>>> {
            val globals = mutableSetOf<IRVar>()
            val blocksMap = mutableMapOf<IRVar, MutableSet<IRLabel>>()
            cfg.blocks.forEach { (label, block) ->
                val varKill = mutableSetOf<IRVar>()
                block.irNodes.forEach { irNode ->
                    irNode.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                        if (rVar !in varKill) {
                            globals.add(rVar)
                        }
                    }
                    irNode.lvalues().forEach { lVar ->
                        varKill.add(lVar)
                        blocksMap.getOrPut(lVar) { mutableSetOf() }.add(label)
                    }
                }
            }
            return globals to blocksMap
        }
    }
}