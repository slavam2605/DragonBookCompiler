package compiler.ir.optimization.clean

import compiler.ir.BaseIRTransformer
import compiler.ir.IRAssign
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRSource
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.phiNodes

class CombineBlocks(private val cfg: ControlFlowGraph) {
    fun invoke(): ControlFlowGraph {
        val pairs = mutableMapOf<IRLabel, IRLabel>()
        cfg.blocks.keys.forEach { fromLabel ->
            val toLabel = cfg.edges(fromLabel).singleOrNull() ?: return@forEach
            if (cfg.backEdges(toLabel).size != 1) return@forEach
            pairs[fromLabel] = toLabel
        }
        if (pairs.isEmpty()) {
            return cfg
        }

        val valueSet = pairs.values.toSet()
        val roots = pairs.keys.filter { it !in valueSet }.toSet()
        (pairs.keys + valueSet - roots).forEach { label ->
            // Check that all phi-nodes in combined blocks have exactly one source
            check(label.phiNodes(cfg).all { it.sources.size == 1 })
        }

        val chains = mutableMapOf<IRLabel, List<IRLabel>>()
        roots.forEach { chainRoot ->
            val chain = mutableListOf(chainRoot)
            var runner = chainRoot
            while (runner in pairs) {
                runner = pairs[runner]!!
                chain.add(runner)
            }
            chains[chainRoot] = chain
        }

        val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
        cfg.blocks.forEach { (label, block) ->
            if (label in chains || label in valueSet) return@forEach
            newBlocks[label] = block
        }
        chains.forEach { (chainRoot, chain) ->
            val irNodes = mutableListOf<IRNode>()
            chain.forEachIndexed { index, chainLabel ->
                val newIRNodes = cfg.blocks[chainLabel]!!.irNodes
                    .dropLast(if (index == chain.lastIndex) 0 else 1)
                    .map {
                        if (index != 0 && it is IRPhi) {
                            IRAssign(it.result, it.sources.single().value)
                        } else it
                    }
                irNodes.addAll(newIRNodes)
            }
            newBlocks[chainRoot] = CFGBlock(irNodes)
        }

        val blockToChainRoot = mutableMapOf<IRLabel, IRLabel>()
        chains.forEach { (chainRoot, chain) ->
            chain.drop(1).forEach { chainLabel ->
                blockToChainRoot[chainLabel] = chainRoot
            }
        }
        newBlocks.forEach { (label, block) ->
            newBlocks[label] = block.transform(object : BaseIRTransformer() {
                override fun transformNode(node: IRNode): IRNode {
                    if (node !is IRPhi) return node
                    val newSources = node.sources.map { (blockLabel, index) ->
                        IRSource(blockToChainRoot[blockLabel] ?: blockLabel, index)
                    }
                    return IRPhi(node.result, newSources)
                }
            })
        }
        return cfg.new(cfg.root, newBlocks)
    }
}