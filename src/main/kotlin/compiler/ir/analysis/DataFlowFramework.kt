package compiler.ir.analysis

import compiler.ir.IRLabel
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ReversedPostOrderTraversal

/**
 * A generic fixed-point data-flow analysis framework over a control-flow graph.
 *
 * @param bottom default value in the lattice
 * @param meet function that merges multiple out-values of predecessors into a single in-value
 * @param transfer function that transforms in-value into out-value for a given block
 * @param modifyOutEdge special function that modifies the out-value along a given edge
 * @param initialOut initializer for out-values
 */
class DataFlowFramework<T>(
    private val cfg: ControlFlowGraph,
    private val bottom: T,
    private val meet: (T, T) -> T,
    private val transfer: (IRLabel, T) -> T,
    private val modifyOutEdge: (from: IRLabel, to: IRLabel, T) -> T = { _, _, v -> v },
    initialOut: ((IRLabel) -> T)? = null
) {
    private val inMap = mutableMapOf<IRLabel, T>()
    private val outMap = mutableMapOf<IRLabel, T>()

    /** Readonly view of in-values per block after run() completes. */
    val inValues: Map<IRLabel, T> get() = inMap

    /** Readonly view of out-values per block after run() completes. */
    val outValues: Map<IRLabel, T> get() = outMap

    init {
        // Initialize OUT values. If not provided, default to `bottom`.
        cfg.blocks.keys.forEach { label ->
            outMap[label] = initialOut?.invoke(label) ?: bottom
        }
    }

    /** Perform the fixed-point iteration until IN/OUT stabilize. */
    fun run() {
        var changed = true
        while (changed) {
            changed = false
            ReversedPostOrderTraversal.traverse(cfg) { label, _ ->
                // Gather predecessor OUT values and meet them to form current IN
                val predOuts = cfg.backEdges(label)
                    .map { modifyOutEdge(it, label, outMap.getOrPut(it) { bottom }) }
                    .ifEmpty { listOf(bottom) }

                val inValue = predOuts.reduce { acc, v -> meet(acc, v) }
                val outValue = transfer(label, inValue)

                val oldIn = inMap[label]
                val oldOut = outMap[label]
                if (oldIn != inValue || oldOut != outValue) {
                    inMap[label] = inValue
                    outMap[label] = outValue
                    changed = true
                }
            }
        }
    }
}
