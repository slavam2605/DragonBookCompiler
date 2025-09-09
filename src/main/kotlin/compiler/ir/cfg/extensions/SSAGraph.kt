package compiler.ir.cfg.extensions

import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey
import compiler.ir.printToString
import java.util.IdentityHashMap

data class IRUse(val blockLabel: IRLabel, val node: IRNode, val opIndex: Int)
data class IRNodeInBlock(val blockLabel: IRLabel, val node: IRNode)

class SSAGraph(
    cfg: ControlFlowGraph,
    private val useDef: Map<IRNode, Array<IRNodeInBlock?>>
) {
    private val defUses: Map<IRNode, Set<IRUse>>

    init {
        val nodeToBlock = mutableMapOf<IRNode, IRLabel>()
        cfg.blocks.forEach { (blockLabel, block) ->
            block.irNodes.forEach { node ->
                nodeToBlock[node] = blockLabel
            }
        }

        val defUses = mutableMapOf<IRNode, MutableSet<IRUse>>()
        useDef.forEach { (node, defs) ->
            defs.forEachIndexed { index, def ->
                if (def != null) {
                    val irUse = IRUse(nodeToBlock[node]!!, node, index)
                    defUses.getOrPut(def.node) { mutableSetOf() }.add(irUse)
                }
            }
        }
        this.defUses = defUses
    }

    fun def(use: IRUse): IRNodeInBlock {
        val defs = checkNotNull(useDef[use.node]) { "Unknown IR node ${use.node.printToString()}" }
        return checkNotNull(defs[use.opIndex]) {
            "Undefined use of variable ${use.node.rvalues().getOrNull(use.opIndex)?.printToString()} " +
                    "in ${use.node.printToString()}"
        }
    }

    fun uses(def: IRNode): Set<IRUse> {
        return defUses[def] ?: emptySet()
    }

    companion object {
        private val Key = ExtensionKey<SSAGraph>("SSAGraph")

        fun get(cfg: ControlFlowGraph) : SSAGraph {
            return cfg.getOrCompute(Key) { compute(cfg) }
        }

        private fun compute(cfg: ControlFlowGraph) : SSAGraph {
            val defMap = mutableMapOf<IRVar, IRNodeInBlock>()
            val useDefMap = IdentityHashMap<IRNode, Array<IRNodeInBlock?>>()
            fun createDefs(node: IRNode): Array<IRNodeInBlock?> {
                check(node !in useDefMap) { "Duplicate IR node $node" }
                return arrayOfNulls<IRNodeInBlock>(node.rvalues().size).also {
                    useDefMap[node] = it
                }
            }

            // Step 1. Initialize definitions for each variable
            cfg.blocks.forEach { (blockLabel, block) ->
                block.irNodes.forEach { node ->
                    node.lvalue?.let { lVar ->
                        defMap[lVar] = IRNodeInBlock(blockLabel, node)
                    }
                }
            }

            // Step 2. Compute use-def chains for each node
            cfg.blocks.forEach { (_, block) ->
                block.irNodes.forEach { node ->
                    val useDefs = createDefs(node)
                    node.rvalues().forEachIndexed { index, irVar ->
                        if (irVar !is IRVar) return@forEachIndexed
                        defMap[irVar]?.let { def ->
                            useDefs[index] = def
                        }
                    }
                }
            }

            return SSAGraph(cfg, useDefMap)
        }
    }
}