package ir.tests

import compiler.ir.IRInt
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRLabel
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.LoopInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class LoopInfoTest {
    private fun runTest(
        edges: Map<String, List<String>>,
        expectedLoopHeaders: Set<String>,
        expectedNestingLevels: Map<String, Int>,
        expectedEdgeWeights: Map<Pair<String, String>, Int>
    ) {
        // Build a fake CFG from given edges
        val blocks = edges
            .mapKeys { (key, _) -> IRLabel(key) }
            .mapValues { (label, outLabels) ->
                when (outLabels.size) {
                    0 -> CFGBlock(emptyList())
                    1 -> CFGBlock(listOf(
                        IRJump(IRLabel(outLabels[0]))
                    ))
                    2 -> CFGBlock(listOf(
                        IRJumpIfTrue(
                            IRInt(0),
                            IRLabel(outLabels[0]),
                            IRLabel(outLabels[1])
                        )
                    ))
                    else -> error("Unsupported number of edges from block ${label.name}: ${outLabels.size}")
                }
            }.toMutableMap()

        // Add all blocks without outgoing edges to the graph
        edges
            .flatMap { it.value }
            .distinct()
            .map { IRLabel(it) }
            .filter { it !in blocks }
            .forEach { blocks[it] = CFGBlock(emptyList()) }

        val cfg = ControlFlowGraph(IRLabel(ENTRY), blocks)
        val loopInfo = LoopInfo.get(cfg)

        val actualLoopHeaders = loopInfo.loops.keys.map { it.name }.toSet()
        val actualNestingLevels = loopInfo.blockNestingLevel
            .mapKeys { (label, _) -> label.name }
        val actualEdgeWeights = loopInfo.edgeNestingLevel
            .mapKeys { (edge, _) -> edge.first.name to edge.second.name }

        assertEquals(expectedLoopHeaders, actualLoopHeaders, "Incorrect loop headers")
        assertEquals(expectedNestingLevels, actualNestingLevels, "Incorrect nesting levels")
        assertEquals(expectedEdgeWeights, actualEdgeWeights, "Incorrect edge weights")
    }

    @Test
    fun testNoLoops() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            B to listOf(C),
            C to emptyList()
        ),
        expectedLoopHeaders = emptySet(),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 0,
            B to 0,
            C to 0
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 0,
            (B to C) to 0
        )
    )

    @Test
    fun testSimpleLoop() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            B to listOf(C),
            C to listOf(B, D), // back edge: C -> B
            D to emptyList()
        ),
        expectedLoopHeaders = setOf(B),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 0,
            B to 1,  // loop header
            C to 1,  // in loop
            D to 0   // after loop
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 0,
            (B to C) to 1,   // inside loop (nesting level 1)
            (C to B) to 1,   // back edge
            (C to D) to 0    // loop exit (nesting level 0)
        )
    )

    @Test
    fun testSelfLoop() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(A, B), // A jumps to itself
            B to emptyList()
        ),
        expectedLoopHeaders = setOf(A),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 1,  // self-loop
            B to 0
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to A) to 1,  // self-loop edge (nesting level 1)
            (A to B) to 0   // exit (nesting level 0)
        )
    )

    @Test
    fun testNestedLoops() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            B to listOf(C, D),  // inner loop header
            C to listOf(B),     // inner loop back edge
            D to listOf(A, E)   // outer loop back edge
        ),
        expectedLoopHeaders = setOf(A, B),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 1,  // outer loop header
            B to 2,  // inner loop header (nested inside outer)
            C to 2,  // in inner loop
            D to 1,  // in outer loop but not inner (D can't reach C without going through B)
            E to 0   // outside all loops
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 1,     // inside outer loop (nesting level 1)
            (B to C) to 2,     // inside both loops (nesting level 2)
            (B to D) to 1,     // B is level 2, D is level 1, common loops = 1
            (C to B) to 2,     // back edge in inner loop (nesting level 2)
            (D to A) to 1,     // back edge in outer loop (nesting level 1)
            (D to E) to 0      // exit outer loop (nesting level 0)
        )
    )

    @Test
    fun testTwoSeparateLoops() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, C),
            B to listOf(A),     // first loop back edge
            C to listOf(D),
            D to listOf(C, E)   // second loop back edge
        ),
        expectedLoopHeaders = setOf(A, C),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 1,  // first loop header
            B to 1,  // in first loop
            C to 1,  // second loop header
            D to 1,  // in second loop
            E to 0   // outside both loops
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 1,   // inside first loop (nesting level 1)
            (A to C) to 0,   // edge between loops (nesting level 0)
            (B to A) to 1,   // back edge first loop (nesting level 1)
            (C to D) to 1,   // inside second loop (nesting level 1)
            (D to C) to 1,   // back edge second loop (nesting level 1)
            (D to E) to 0    // exit second loop (nesting level 0)
        )
    )

    @Test
    fun testLoopWithMultipleExits() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, E),  // loop header with exit
            B to listOf(C, D),
            C to listOf(A),     // back edge
            D to listOf(E),     // another exit
            E to emptyList()
        ),
        expectedLoopHeaders = setOf(A),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 1,  // loop header
            B to 1,  // in loop (can reach C, which is the tail)
            C to 1,  // in loop (is the tail)
            D to 0,  // NOT in loop (cannot reach tail C)
            E to 0   // outside loop
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 1,   // inside loop (nesting level 1)
            (A to E) to 0,   // loop exit (nesting level 0)
            (B to C) to 1,   // inside loop (nesting level 1)
            (B to D) to 0,   // B in loop, D not in loop (nesting level 0)
            (C to A) to 1,   // back edge (nesting level 1)
            (D to E) to 0    // outside loop (nesting level 0)
        )
    )

    @Test
    fun testIrreducibleLoop() = runTest(
        // Irreducible loop structure: Entry -> A, A can go to B or D
        // B -> C -> D, D -> B creates a cycle
        // This is irreducible because you can enter the cycle at multiple points
        // Since D doesn't dominate B and B doesn't dominate D, neither edge is a back edge
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, D),  // can enter at B or D
            B to listOf(C),
            C to listOf(D),
            D to listOf(B, E),  // cycle D -> B, but also exit to E
            E to emptyList()
        ),
        // No back edges detected because neither B nor D dominates the other
        expectedLoopHeaders = emptySet(),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 0,
            B to 0,
            C to 0,
            D to 0,
            E to 0
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 0,
            (A to D) to 0,
            (B to C) to 0,
            (C to D) to 0,
            (D to B) to 0,
            (D to E) to 0
        )
    )

    @Test
    fun testDeeplyNestedLoops() = runTest(
        // Three levels of nesting: A -> B -> C with back edges
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, F),  // level 1 loop header
            B to listOf(C, E),  // level 2 loop header
            C to listOf(D),     // level 3 loop header
            D to listOf(C, B),  // back edge to C (exits level 3)
            E to listOf(A),     // back edge to A
            F to emptyList()
        ),
        expectedLoopHeaders = setOf(A, B, C),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 1,  // level 1 loop
            B to 2,  // level 2 loop (inside A)
            C to 3,  // level 3 loop (inside B)
            D to 3,  // in level 3 loop
            E to 1,  // E is only in loop A (natural loop from E->A doesn't include B or C)
            F to 0   // outside all loops
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 1,      // inside level 1 (nesting level 1)
            (A to F) to 0,      // exit level 1 (nesting level 0)
            (B to C) to 2,      // inside level 2 (nesting level 2)
            (B to E) to 1,      // B is level 2, E is level 1, common loops = 1
            (C to D) to 3,      // inside level 3 (nesting level 3)
            (D to C) to 3,      // back edge level 3 (nesting level 3)
            (D to B) to 2,      // exit level 3, D and B both in B's loop (nesting level 2)
            (E to A) to 1       // back edge level 1 (nesting level 1)
        )
    )

    @Test
    fun testComplexLoopStructure() = runTest(
        // Loop with internal branching and multiple back edges
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),     // loop header
            B to listOf(C, D),
            C to listOf(E),
            D to listOf(E),
            E to listOf(B, F)   // back edge to B
        ),
        expectedLoopHeaders = setOf(B),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            A to 0,
            B to 1,  // loop header
            C to 1,  // in loop
            D to 1,  // in loop
            E to 1,  // in loop
            F to 0   // outside loop
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            (A to B) to 0,
            (B to C) to 1,
            (B to D) to 1,
            (C to E) to 1,
            (D to E) to 1,
            (E to B) to 1,   // back edge (nesting level 1)
            (E to F) to 0    // loop exit (nesting level 0)
        )
    )

    @Test
    fun testTwoComplexSeparateLoops() = runTest(
        // Two separate loops, each with internal structure
        // First loop: A -> B -> C/D -> E -> A (diamond pattern inside loop)
        // Second loop: F -> G -> H/I -> J -> F (diamond pattern inside loop)
        mapOf(
            ENTRY to listOf(A),
            // First loop with diamond structure
            A to listOf(B, F),   // loop header, can exit to second loop
            B to listOf(C, D),   // branch inside first loop
            C to listOf(E),      // left path
            D to listOf(E),      // right path
            E to listOf(A),      // back edge to first loop header
            // Second loop with diamond structure
            F to listOf(G),      // second loop header
            G to listOf(H, I),   // branch inside second loop
            H to listOf(J),      // left path
            I to listOf(J),      // right path
            J to listOf(F, K)    // back edge to second loop header, can exit
        ),
        expectedLoopHeaders = setOf(A, F),
        expectedNestingLevels = mapOf(
            ENTRY to 0,
            // First loop
            A to 1,  // loop header
            B to 1,  // in first loop
            C to 1,  // in first loop
            D to 1,  // in first loop
            E to 1,  // in first loop
            // Second loop
            F to 1,  // loop header
            G to 1,  // in second loop
            H to 1,  // in second loop
            I to 1,  // in second loop
            J to 1,  // in second loop
            K to 0   // outside both loops
        ),
        expectedEdgeWeights = mapOf(
            (ENTRY to A) to 0,
            // First loop edges
            (A to B) to 1,    // inside first loop (nesting level 1)
            (A to F) to 0,    // exit first loop, enter second loop (nesting level 0)
            (B to C) to 1,    // inside first loop (nesting level 1)
            (B to D) to 1,    // inside first loop (nesting level 1)
            (C to E) to 1,    // inside first loop (nesting level 1)
            (D to E) to 1,    // inside first loop (nesting level 1)
            (E to A) to 1,    // back edge first loop (nesting level 1)
            // Second loop edges
            (F to G) to 1,    // inside second loop (nesting level 1)
            (G to H) to 1,    // inside second loop (nesting level 1)
            (G to I) to 1,    // inside second loop (nesting level 1)
            (H to J) to 1,    // inside second loop (nesting level 1)
            (I to J) to 1,    // inside second loop (nesting level 1)
            (J to F) to 1,    // back edge second loop (nesting level 1)
            (J to K) to 0     // exit second loop (nesting level 0)
        )
    )

    companion object {
        private const val ENTRY = "entry"
        private const val A = "A"
        private const val B = "B"
        private const val C = "C"
        private const val D = "D"
        private const val E = "E"
        private const val F = "F"
        private const val G = "G"
        private const val H = "H"
        private const val I = "I"
        private const val J = "J"
        private const val K = "K"
    }
}
