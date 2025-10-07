package compiler.ir.cfg.extensions

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey

/**
 * Represents loop structure information for various optimizations.
 */
class LoopInfo(
    /**
     * Map from loop headers to loop structures
     */
    val loops: Map<IRLabel, Loop>,

    /**
     * Nesting level for each block (0 = not in any loop, 1 = in one loop, etc.)
     */
    val blockNestingLevel: Map<IRLabel, Int>,

    /**
     * Nesting level for each edge in the CFG. Entry and exit edges from the loop
     * are considered to be outside that loop.
     */
    val edgeNestingLevel: Map<Pair<IRLabel, IRLabel>, Int>
) {
    /**
     * Represents a single loop structure with its header and body.
     */
    data class Loop(
        /**
         * The loop header block (target of back edges)
         */
        val header: IRLabel,

        /**
         * Set of all blocks in the loop body (including the header)
         */
        val body: Set<IRLabel>
    )

    companion object {
        private val Key = ExtensionKey<LoopInfo>("LoopInfo")

        fun get(cfg: ControlFlowGraph): LoopInfo {
            return cfg.getOrCompute(Key) { compute(cfg) }
        }

        private fun compute(cfg: ControlFlowGraph): LoopInfo {
            val domTree = DominatorTree.get(cfg)
            val dominance = CFGDominance.get(cfg)

            // Find back edges: B → H where H dominates B
            val backEdges = mutableListOf<Pair<IRLabel, IRLabel>>()
            cfg.blocks.forEach { (from, _) ->
                cfg.edges(from).forEach { to ->
                    if (to in (dominance[from] ?: emptySet())) {
                        backEdges.add(from to to)
                    }
                }
            }

            // Build loop information
            val loopHeaders = backEdges.map { it.second }.toSet()
            val loopBody = mutableMapOf<IRLabel, Set<IRLabel>>()

            // For each back edge, compute loop body using natural loop algorithm
            backEdges.forEach { (tail, head) ->
                val body = findNaturalLoop(cfg, head, tail)
                loopBody[head] = (loopBody[head] ?: emptySet()) + body
            }

            // Create Loop structures map
            val loops = loopHeaders.associateWith { header ->
                val body = loopBody[header] ?: error("Loop header $header has no body")
                Loop(header, body)
            }

            // Compute nesting levels: count how many loop headers dominate each block
            val nestingLevel = cfg.blocks.keys.associateWith { block ->
                var level = 0
                var current: IRLabel? = block
                while (current != null) {
                    if (current in loopHeaders && block in (loopBody[current] ?: emptySet())) {
                        level++
                    }
                    current = domTree.iDom(current)
                }
                level
            }

            val edgeNestingLevel = mutableMapOf<Pair<IRLabel, IRLabel>, Int>()
            cfg.blocks.forEach { (from, _) ->
                cfg.edges(from).forEach { to ->
                    // Find the deepest common loop containing both from and to
                    // by checking which loops have both nodes in their body
                    val commonLoopLevel = loopHeaders.count { header ->
                        val body = loopBody[header]!!
                        from in body && to in body
                    }

                    edgeNestingLevel[from to to] = commonLoopLevel
                }
            }

            return LoopInfo(loops, nestingLevel, edgeNestingLevel)
        }

        /**
         * Find natural loop: all blocks that can reach [tail] without going through [head].
         * The natural loop is defined as the set of nodes dominated by the loop header
         * that can reach the back edge tail without leaving the loop.
         */
        private fun findNaturalLoop(cfg: ControlFlowGraph, head: IRLabel, tail: IRLabel): Set<IRLabel> {
            // Special case: self-loop (head == tail)
            if (head == tail) {
                return setOf(head)
            }

            val loop = mutableSetOf(head, tail)
            val worklist = mutableListOf(tail)

            while (worklist.isNotEmpty()) {
                val block = worklist.removeAt(worklist.lastIndex)
                cfg.backEdges(block).forEach { pred ->
                    // Stop when reaching the head (don't go past it)
                    if (pred !in loop) {
                        loop.add(pred)
                        worklist.add(pred)
                    }
                }
            }

            return loop
        }
    }
}
