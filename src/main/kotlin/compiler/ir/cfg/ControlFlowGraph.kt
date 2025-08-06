package compiler.ir.cfg

import compiler.ir.IRJump
import compiler.ir.IRJumpNode
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRProtoNode
import compiler.ir.printToString

class ControlFlowGraph(
    val root: IRLabel,
    val blocks: Map<IRLabel, CFGBlock>
) : ExtensionHolder() {
    val edges: Map<IRLabel, Set<IRLabel>>
    val backEdges: Map<IRLabel, Set<IRLabel>>

    init {
        blocks.forEach { (label, block) ->
            // Skip empty blocks
            if (block.irNodes.isEmpty()) return@forEach

            // Check that all nodes except the last one are not jumps
            block.irNodes.subList(0, block.irNodes.size - 1).forEach {
                if (it is IRJumpNode) {
                    error("Jump node '${it.printToString()}' is not the last in block $label")
                }
            }

            // TODO check that last node is a jump or a "ret" (when functions will be implemented)
        }

        edges = blocks.mapValues { (_, block) -> block.irNodes.filterIsInstance<IRJumpNode>().flatMap { it.labels() }.toSet() }
        backEdges = blocks.mapValues { (label, _) -> blocks.keys.filter { label in (edges[it] ?: emptySet()) }.toSet() }
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
                        if (currentBlock.isEmpty() || currentBlock.last() !is IRJumpNode) {
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