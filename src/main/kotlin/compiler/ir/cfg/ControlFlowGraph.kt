package compiler.ir.cfg

import compiler.ir.IRJump
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRProtoNode

class ControlFlowGraph(
    val root: IRLabel,
    val blocks: Map<IRLabel, CFGBlock>
) {
    val edges: Map<IRLabel, Set<IRLabel>> = blocks.mapValues { (_, block) ->
        block.irNodes
            .flatMap { it.labels() }
            .toSet()
    }

    companion object {
        private val Root = IRLabel("<root>")

        fun build(protoIr: List<IRProtoNode>): ControlFlowGraph {
            val nodes = mutableMapOf<IRLabel, CFGBlock>()
            var currentLabel = Root
            var currentBlock = mutableListOf<IRNode>()
            for (protoNode in protoIr) {
                when (protoNode) {
                    is IRLabel -> {
                        if (currentBlock.isEmpty() || currentBlock.last() !is IRJump) {
                            // Add an explicit jump to the next block
                            currentBlock.add(IRJump(protoNode))
                        }
                        nodes[currentLabel] = CFGBlock(currentBlock)
                        currentLabel = protoNode
                        currentBlock = mutableListOf()
                    }
                    is IRNode -> {
                        currentBlock.add(protoNode)
                    }
                }
            }
            // TODO add explicit "ret" here?
            nodes[currentLabel] = CFGBlock(currentBlock)
            return RemoveUnusedNodes.invoke(ControlFlowGraph(Root, nodes))
        }
    }
}