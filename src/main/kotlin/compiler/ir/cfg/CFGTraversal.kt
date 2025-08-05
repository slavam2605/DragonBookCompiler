package compiler.ir.cfg

import compiler.ir.IRLabel

fun interface CFGVisitor {
    fun visit(label: IRLabel, block: CFGBlock)
}

interface CFGTraversal {
    /**
     * Traverses all blocks in the control flow graph that are reachable from the [ControlFlowGraph.root], using the provided [visitor].
     *
     * Different implementations represent different traversal orders.
     */
    fun traverse(cfg: ControlFlowGraph, visitor: CFGVisitor)
}

/**
 * Post-order traversal. First visits all the children of the node, then the node itself.
 */
object PostOrderTraversal : CFGTraversal {
    override fun traverse(cfg: ControlFlowGraph, visitor: CFGVisitor) {
        val visited = mutableSetOf<IRLabel>()

        // Visit the root first
        traversePostOrder(cfg.root, cfg, visited, visitor)

        // Visit all blocks that are not reachable from the root
        for (label in cfg.blocks.keys) {
            if (label !in visited) {
                traversePostOrder(label, cfg, visited, visitor)
            }
        }
    }

    private fun traversePostOrder(current: IRLabel, cfg: ControlFlowGraph,
                                  visited: MutableSet<IRLabel>, visitor: CFGVisitor) {
        if (current in visited) return
        visited.add(current)

        val successors = cfg.edges[current] ?: emptyList()
        for (successor in successors) {
            traversePostOrder(successor, cfg, visited, visitor)
        }

        val block = cfg.blocks[current] ?: error("No block for label $current")
        visitor.visit(current, block)
    }
}

/**
 * Reverse post-order traversal. Visits all parents of a node before the node itself.
 */
object ReversedPostOrderTraversal : CFGTraversal {
    override fun traverse(cfg: ControlFlowGraph, visitor: CFGVisitor) {
        val postOrder = mutableListOf<IRLabel>()
        PostOrderTraversal.traverse(cfg) { label, _ ->
            postOrder.add(label)
        }
        postOrder.asReversed().forEach { label ->
            val block = cfg.blocks[label] ?: error("No block for label $label")
            visitor.visit(label, block)
        }
    }
}