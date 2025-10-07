package compiler.backend.arm64

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.LoopInfo

object BlocksSorter {
    fun sort(cfg: ControlFlowGraph): List<IRLabel> {
        val loopInfo = LoopInfo.get(cfg)
        return computeBlockOrder(cfg, loopInfo)
    }

    /**
     * Computes optimal block ordering using a greedy algorithm that minimizes
     * the total cost of edges that require explicit branches.
     *
     * Cost model:
     * - If blocks A and B are consecutive and A→B exists, cost = 0 (fall-through)
     * - Otherwise, cost = edge_weight(A→B) from loop analysis
     *
     * Algorithm: Greedy chain-building
     * 1. Start with the root block
     * 2. At each step, follow the highest-weight outgoing edge to an unvisited block
     * 3. If no unvisited successors, pick the highest-weight unvisited block
     */
    private fun computeBlockOrder(cfg: ControlFlowGraph, loopInfo: LoopInfo): List<IRLabel> {
        val resultOrder = mutableListOf<IRLabel>()
        val visited = mutableSetOf<IRLabel>()

        fun visit(block: IRLabel) {
            if (block in visited) return
            visited.add(block)
            resultOrder.add(block)

            // Find the best successor to visit next (highest nesting level edge to unvisited block)
            val bestSuccessor = cfg.edges(block)
                .filter { it !in visited }
                .maxByOrNull { successor ->
                    // Prefer edges with higher nesting level (more deeply nested in loops)
                    // This keeps loop bodies together in the generated code
                    loopInfo.edgeNestingLevel[block to successor]!!
                }

            if (bestSuccessor != null) {
                visit(bestSuccessor)
            }
        }

        // Start with root
        visit(cfg.root)

        // Visit any remaining unvisited blocks (unreachable code)
        // Prioritize blocks with higher nesting level (likely more important)
        cfg.blocks.keys
            .filter { it !in visited }
            .sortedByDescending { loopInfo.blockNestingLevel[it] ?: 0 }
            .forEach { visit(it) }

        return resultOrder
    }
}