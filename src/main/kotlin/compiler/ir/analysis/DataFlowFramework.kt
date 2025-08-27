package compiler.ir.analysis

import compiler.ir.IRLabel
import compiler.ir.cfg.CFGTraversal
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ReversedPostOrderTraversal
import compiler.ir.cfg.PostOrderTraversal

/**
 * A generic fixed-point data-flow analysis framework over a control-flow graph.
 *
 * @param direction direction of the data flow problem:
 *  - `FORWARD`: from predecessor to successor in reverse post-order
 *  - `BACKWARD`: from successor to predecessor in post-order
 * @param identity lattice identity value, used for `meet(<empty-set>)`
 * @param meet function that merges multiple out-values of predecessors into a single in-value
 * @param transfer function that transforms in-value into out-value for a given block
 * @param modifyEdgeValue special function that modifies the value along a given edge.
 *  Receives `(from, to)` CFG edge for forward analysis, and `(to, from)` CFG edge for backward analysis.
 * @param boundaryIn fixed in-values for boundary (entry) blocks, `identity` if a result is null
 * @param boundaryOut fixed out-values for boundary (exit) blocks, `identity` if a result is null
 * @param initialIn initializer for in-values
 * @param initialOut initializer for out-values
 */
class DataFlowFramework<T : Any>(
    private val cfg: ControlFlowGraph,
    private val direction: Direction,
    private val identity: T,
    private val meet: (acc: T, T) -> T,
    private val transfer: (IRLabel, T) -> T,
    private val modifyEdgeValue: (from: IRLabel, to: IRLabel, T) -> T = { _, _, v -> v },
    private val boundaryIn: (IRLabel) -> T? = { null },
    private val boundaryOut: (IRLabel) -> T? = { null },
    initialIn: ((IRLabel) -> T)? = null,
    initialOut: ((IRLabel) -> T)? = null
) {
    private val inMap = mutableMapOf<IRLabel, T>()
    private val outMap = mutableMapOf<IRLabel, T>()

    /** Readonly view of in-values per block after run() completes. */
    val inValues: Map<IRLabel, T> get() = inMap

    /** Readonly view of out-values per block after run() completes. */
    val outValues: Map<IRLabel, T> get() = outMap

    init {
        check(initialIn != null || initialOut != null)
        cfg.blocks.keys.forEach { label ->
            when (direction) {
                Direction.FORWARD -> {
                    outMap[label] = initialOut?.invoke(label) ?: transfer(label, initialIn!!(label))
                }
                Direction.BACKWARD -> {
                    inMap[label] = initialIn?.invoke(label) ?: transfer(label, initialOut!!(label))
                }
            }
        }
    }

    /** Perform the fixed-point iteration until IN/OUT stabilize. */
    fun run(): DataFlowFramework<T> {
        var changed = true
        while (changed) {
            changed = false
            val traversal = direction.traversal()
            traversal.traverse(cfg) { label, _ ->
                val fromValues = direction.edges(cfg, label).map { from ->
                    val blockValue = direction.chooseEnd(inMap, outMap)[from]!!
                    modifyEdgeValue(from, label, blockValue)
                }
                val startValue = if (fromValues.isNotEmpty()) {
                    fromValues.fold(identity) { acc, v -> meet(acc, v) }
                } else {
                    direction.chooseStart(boundaryIn, boundaryOut)(label) ?: identity
                }
                val endValue = transfer(label, startValue)

                val oldIn = inMap[label]
                val oldOut = outMap[label]
                val (inValue, outValue) = direction.sortInOut(startValue, endValue)
                if (oldIn != inValue || oldOut != outValue) {
                    inMap[label] = inValue
                    outMap[label] = outValue
                    changed = true
                }
            }
        }
        return this
    }

    enum class Direction {
        FORWARD {
            override fun traversal() = ReversedPostOrderTraversal
            override fun edges(cfg: ControlFlowGraph, label: IRLabel) = cfg.backEdges(label)
            override fun <T> chooseStart(inValue: T, outValue: T) = inValue
            override fun <T> chooseEnd(inValue: T, outValue: T) = outValue
            override fun <T> sortInOut(startValue: T, endValue: T) = startValue to endValue
        },
        BACKWARD {
            override fun traversal() = PostOrderTraversal
            override fun edges(cfg: ControlFlowGraph, label: IRLabel) = cfg.edges(label)
            override fun <T> chooseStart(inValue: T, outValue: T) = outValue
            override fun <T> chooseEnd(inValue: T, outValue: T) = inValue
            override fun <T> sortInOut(startValue: T, endValue: T) = endValue to startValue
        };

        abstract fun traversal(): CFGTraversal
        abstract fun edges(cfg: ControlFlowGraph, label: IRLabel): Set<IRLabel>
        abstract fun <T> chooseStart(inValue: T, outValue: T): T
        abstract fun <T> chooseEnd(inValue: T, outValue: T): T
        abstract fun <T> sortInOut(startValue: T, endValue: T): Pair<T, T>
    }
}
