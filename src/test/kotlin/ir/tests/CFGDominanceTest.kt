package ir.tests

import compiler.ir.IRInt
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRLabel
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.CFGDominance
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.DominanceFrontiers
import compiler.ir.cfg.DominatorTree
import kotlin.test.Test
import kotlin.test.assertEquals

class CFGDominanceTest {
    private fun runTest(
        edges: Map<String, List<String>>, 
        expectedDom: Map<String, Set<String>>,
        expectedIDom: Map<String, String>
    ) {
        // Build a fake CFG from given edges
        val blocks = edges
            .mapKeys { (key, _) -> IRLabel(key) }
            .mapValues { (label, outLabels) ->
                when (outLabels.size) {
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
        val dom = CFGDominance.get(cfg)
            .mapKeys { (label, _) -> label.name }
            .mapValues { (_, dominators) -> dominators.map { it.name }.toSet() }
        val iDom = DominatorTree.get(cfg)
            .mapKeys { (label, _) -> label.name }
            .mapValues { (_, label) -> label.name }
        val df = DominanceFrontiers.get(cfg)
            .mapKeys { (label, _) -> label.name }
            .mapValues { (_, frontiers) -> frontiers.map { it.name }.toSet() }
        val slowCorrectDF = correctReferenceDF(cfg)

        assertEquals(expectedDom, dom, "Incorrect dominance sets")
        assertEquals(expectedIDom, iDom, "Incorrect immediate dominator tree")
        assertEquals(slowCorrectDF, df, "Incorrect dominance frontiers")
    }

    // Slow implementation of DF which uses the definition of Dominance Frontier
    private fun correctReferenceDF(cfg: ControlFlowGraph): Map<String, Set<String>> {
        val dom = CFGDominance.get(cfg)
        return cfg.blocks.mapValues { (label, _) ->
            cfg.blocks.keys.filter { otherLabel ->
                // 1. `label` dominates at least one predecessor of `otherLabel`
                cfg.backEdges(otherLabel).any { pred -> label in dom[pred]!! } &&
                // 2. `label` does not strictly dominate `otherLabel`
                label !in (dom[otherLabel]!! - otherLabel)
            }.map { it.name }.toSet()
        }.mapKeys { (label, _) -> label.name }
    }

    @Test
    fun testLinear() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            B to listOf(C)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, B, C)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to B
        )
    )

    @Test
    fun testIfThenJoin() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, C),
            B to listOf(D),
            C to listOf(D)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, C),
            D to setOf(ENTRY, A, D)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to A,
            D to A
        )
    )

    @Test
    fun testDiamond() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, C),
            B to listOf(D),
            C to listOf(D),
            D to listOf(E)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, C),
            D to setOf(ENTRY, A, D),
            E to setOf(ENTRY, A, D, E)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to A,
            D to A,
            E to D
        )
    )

    @Test
    fun testLoop() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            B to listOf(C),
            C to listOf(B, D)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, B, C),
            D to setOf(ENTRY, A, B, C, D)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to B,
            D to C
        )
    )

    @Test
    fun testTwoLoops() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, C),
            B to listOf(A),
            C to listOf(D),
            D to listOf(C, E)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, C),
            D to setOf(ENTRY, A, C, D),
            E to setOf(ENTRY, A, C, D, E)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to A,
            D to C,
            E to D
        )
    )

    @Test
    fun testUnreachable() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            C to listOf(D)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(C),
            D to setOf(C, D)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            D to C
        )
    )

    @Test
    fun testMultipleExits() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, C),
            B to listOf(D),
            C to listOf(E)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, C),
            D to setOf(ENTRY, A, B, D),
            E to setOf(ENTRY, A, C, E)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to A,
            D to B,
            E to C
        )
    )

    @Test
    fun testIrreducibleLoop() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, D),
            B to listOf(C),
            C to listOf(D),
            D to listOf(B)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, B, C),
            D to setOf(ENTRY, A, D)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to B,
            D to A
        )
    )

    @Test
    fun testSelfLoop() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(A, B)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B)
        ),
        mapOf(
            A to ENTRY,
            B to A
        )
    )

    @Test
    fun testNestedLoops() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B),
            B to listOf(C, D),
            C to listOf(B), // inner loop
            D to listOf(A, E)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, B, C),
            D to setOf(ENTRY, A, B, D),
            E to setOf(ENTRY, A, B, D, E)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to B,
            D to B,
            E to D
        )
    )

    @Test
    fun testComplexWithEntry() = runTest(
        mapOf(
            ENTRY to listOf(A),
            A to listOf(B, C),
            B to listOf(D),
            D to listOf(E, F),
            E to listOf(G),
            F to listOf(G),
            G to listOf(D, H),
            C to listOf(I),
            H to listOf(J),
            I to listOf(J)
        ),
        mapOf(
            ENTRY to setOf(ENTRY),
            A to setOf(ENTRY, A),
            B to setOf(ENTRY, A, B),
            C to setOf(ENTRY, A, C),
            D to setOf(ENTRY, A, B, D),
            E to setOf(ENTRY, A, B, D, E),
            F to setOf(ENTRY, A, B, D, F),
            G to setOf(ENTRY, A, B, D, G),
            H to setOf(ENTRY, A, B, D, G, H),
            I to setOf(ENTRY, A, C, I),
            J to setOf(ENTRY, A, J)
        ),
        mapOf(
            A to ENTRY,
            B to A,
            C to A,
            D to B,
            E to D,
            F to D,
            G to D,
            H to G,
            I to C,
            J to A
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
    }
}