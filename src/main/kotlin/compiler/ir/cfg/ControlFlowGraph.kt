package compiler.ir.cfg

import compiler.ir.IRJump
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRProtoNode

class ControlFlowGraph(
    val root: IRLabel,
    val nodes: Map<IRLabel, CFGBlock>
) {
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
            return ControlFlowGraph(Root, nodes)
        }
    }
}