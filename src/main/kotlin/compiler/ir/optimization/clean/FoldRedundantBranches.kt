package compiler.ir.optimization.clean

import compiler.ir.BaseIRTransformer
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRNode
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.hasPhiNodes

class FoldRedundantBranches(private val cfg: ControlFlowGraph) {
    fun invoke(): ControlFlowGraph {
        var changed = false
        val newBlocks = cfg.blocks.mapValues { (_, block) ->
            block.transform(object : BaseIRTransformer() {
                override fun transformNode(node: IRNode): IRNode {
                    if (node !is IRJumpIfTrue) return node
                    if (node.target != node.elseTarget) return node
                    check(!node.target.hasPhiNodes(cfg))
                    changed = true
                    return IRJump(node.target)
                }
            })
        }
        if (!changed) return cfg
        return cfg.new(cfg.root, newBlocks)
    }
}