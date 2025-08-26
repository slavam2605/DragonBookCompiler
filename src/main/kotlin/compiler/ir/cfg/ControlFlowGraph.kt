package compiler.ir.cfg

import compiler.ir.IRJump
import compiler.ir.IRJumpNode
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRProtoNode
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.optimization.clean.CleanCFG
import compiler.ir.printToString

open class ControlFlowGraph(
    val root: IRLabel,
    val blocks: Map<IRLabel, CFGBlock>
) : ExtensionHolder() {
    private val edges: Map<IRLabel, Set<IRLabel>>
    private val backEdges: Map<IRLabel, Set<IRLabel>>

    fun edges(label: IRLabel): Set<IRLabel> = edges[label] ?: emptySet()
    fun backEdges(label: IRLabel): Set<IRLabel> = backEdges[label] ?: emptySet()

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

        edges = blocks.mapValues { (_, block) ->
            block.irNodes.filterIsInstance<IRJumpNode>().flatMap { it.labels() }.toSet()
        }
        backEdges = blocks.mapValues { (label, _) ->
            blocks.keys.filter { label in edges(it) }.toSet()
        }

        // Check that the entry block does not have back edges
        check(backEdges(root).isEmpty()) {
            // Having back edges of the entry blocks breaks data-flow analysis. In such cases
            // the control-flow graph doesn't know that we can enter the entry block from the outside.
            // We have to keep a separate entry block for this purpose
            "Entry block $root has back edges: ${backEdges(root).joinToString(", ") { it.name }}"
        }
    }

    open fun new(root: IRLabel, blocks: Map<IRLabel, CFGBlock>): ControlFlowGraph {
        return ControlFlowGraph(root, blocks)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ControlFlowGraph
        if (root != other.root) return false
        if (blocks != other.blocks) return false
        return true
    }

    override fun hashCode(): Int {
        var result = root.hashCode()
        result = 31 * result + blocks.hashCode()
        return result
    }

    companion object {
        private val Root = IRLabel("<root>")

        fun build(protoIr: List<IRProtoNode>, sourceMap: SourceLocationMap): ControlFlowGraph {
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
            val cfg = ControlFlowGraph(Root, nodes).apply {
                SourceLocationMap.storeMap(sourceMap, this)
            }
            return CleanCFG.invoke(cfg)
        }
    }
}