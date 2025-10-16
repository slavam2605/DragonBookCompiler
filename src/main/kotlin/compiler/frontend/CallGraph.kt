package compiler.frontend

import compiler.ir.IRFunctionCall
import compiler.ir.cfg.ControlFlowGraph

/**
 * Represents the call graph of a program, showing which functions call which other functions.
 *
 * This is useful for:
 * - Detecting recursive functions
 * - Computing optimal inlining order (leaves first)
 * - Analyzing inter-procedural dependencies
 */
class CallGraph<T>(
    private val ffs: FrontendFunctions<T>,
    private val cfgGetter: (T) -> ControlFlowGraph
) {
    // Map from function name to set of functions it calls
    private val callees: Map<String, Set<String>>

    // Map from function name to set of functions that call it
    private val callers: Map<String, Set<String>>

    init {
        val calleesMap = mutableMapOf<String, MutableSet<String>>()
        val callersMap = mutableMapOf<String, MutableSet<String>>()

        // Initialize maps with all function names
        ffs.values.forEach { fn ->
            calleesMap[fn.name] = mutableSetOf()
            callersMap[fn.name] = mutableSetOf()
        }

        // Build call graph edges
        ffs.values.forEach { fn ->
            val cfg = cfgGetter(fn.value)
            val calledFunctions = findCalledFunctions(cfg)

            calledFunctions.forEach { calleeName ->
                calleesMap[fn.name]!!.add(calleeName)
                callersMap.getOrPut(calleeName) { mutableSetOf() }.add(fn.name)
            }
        }

        callees = calleesMap
        callers = callersMap
    }

    /**
     * Returns the set of functions called by the given function.
     */
    fun getCallees(functionName: String): Set<String> {
        return callees[functionName] ?: emptySet()
    }

    /**
     * Returns the set of functions that call the given function.
     */
    fun getCallers(functionName: String): Set<String> {
        return callers[functionName] ?: emptySet()
    }

    /**
     * Returns true if the function is recursive (directly or indirectly calls itself).
     */
    fun isRecursive(functionName: String): Boolean {
        return isInCycle(functionName)
    }

    /**
     * Returns the set of all functions that are part of recursive cycles.
     */
    fun getRecursiveFunctions(): Set<String> {
        return ffs.values.map { it.name }.filter { isRecursive(it) }.toSet()
    }

    /**
     * Returns true if the function is a leaf (doesn't call any other functions).
     */
    fun isLeaf(functionName: String): Boolean {
        return getCallees(functionName).isEmpty()
    }

    /**
     * Returns all leaf functions (functions that don't call any other functions).
     */
    fun getLeafFunctions(): Set<String> {
        return ffs.values.map { it.name }.filter { isLeaf(it) }.toSet()
    }

    /**
     * Returns strongly connected components in topological order.
     *
     * Each SCC is a set of mutually recursive functions. SCCs are ordered such that
     * if SCC A calls functions in SCC B, then B appears before A in the list.
     *
     * Within each SCC, the order of functions is arbitrary (they're all mutually recursive).
     *
     * This is useful for inlining:
     * - Process SCCs in order (leaves first)
     * - Singleton SCCs (size == 1) without self-recursion can be safely inlined
     * - Multi-function SCCs (size > 1) are mutually recursive - can't inline within the SCC
     * - You can always inline functions from earlier SCCs into later ones
     *
     * @return List of SCCs in topological order (leaves first), each SCC is a set of function names
     */
    fun getSCCsInTopologicalOrder(): List<Set<String>> {
        val sccs = computeSCCs()
        return topologicalSortSCCs(sccs)
    }

    /**
     * Returns true if the SCC represents recursive functions.
     *
     * An SCC is recursive if:
     * - It contains more than one function (mutual recursion)
     * - It contains one function that calls itself (self-recursion)
     */
    fun isSCCRecursive(scc: Set<String>): Boolean {
        if (scc.size > 1) return true
        if (scc.size == 1) {
            val fn = scc.first()
            return fn in getCallees(fn) // Self-recursive
        }
        return false
    }

    /**
     * Returns true if the function is part of a cycle in the call graph.
     */
    private fun isInCycle(functionName: String): Boolean {
        val visited = mutableSetOf<String>()
        val recursiveStack = mutableSetOf<String>()

        fun hasCycle(name: String): Boolean {
            if (name in recursiveStack) return true
            if (name in visited) return false

            visited.add(name)
            recursiveStack.add(name)

            for (callee in getCallees(name)) {
                if (callee in ffs.values.map { it.name } && hasCycle(callee)) {
                    return true
                }
            }

            recursiveStack.remove(name)
            return false
        }

        return hasCycle(functionName)
    }

    /**
     * Computes strongly connected components using Tarjan's algorithm.
     */
    private fun computeSCCs(): List<Set<String>> {
        val functionNames = ffs.values.map { it.name }.toSet()
        val indices = mutableMapOf<String, Int>()
        val lowlinks = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = mutableListOf<String>()
        val sccs = mutableListOf<Set<String>>()
        var index = 0

        fun strongConnect(name: String) {
            indices[name] = index
            lowlinks[name] = index
            index++
            stack.add(name)
            onStack.add(name)

            // Consider successors (callees)
            for (callee in getCallees(name)) {
                if (callee !in functionNames) continue // Skip external functions

                when {
                    callee !in indices -> {
                        // Successor has not yet been visited; recurse
                        strongConnect(callee)
                        lowlinks[name] = minOf(lowlinks[name]!!, lowlinks[callee]!!)
                    }
                    callee in onStack -> {
                        // Successor is on stack and hence in the current SCC
                        lowlinks[name] = minOf(lowlinks[name]!!, indices[callee]!!)
                    }
                }
            }

            // If name is a root node, pop the stack and create an SCC
            if (lowlinks[name] == indices[name]) {
                val scc = mutableSetOf<String>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    scc.add(w)
                    if (w == name) break
                }
                sccs.add(scc)
            }
        }

        // Run Tarjan's algorithm from all unvisited nodes
        for (fn in ffs.values) {
            if (fn.name !in indices) {
                strongConnect(fn.name)
            }
        }

        return sccs
    }

    /**
     * Topologically sorts SCCs based on dependencies between them.
     */
    private fun topologicalSortSCCs(sccs: List<Set<String>>): List<Set<String>> {
        // Build SCC graph: map each function to its SCC
        val functionToSCC = mutableMapOf<String, Set<String>>()
        sccs.forEach { scc ->
            scc.forEach { fn -> functionToSCC[fn] = scc }
        }

        // Build edges between SCCs
        val sccEdges = mutableMapOf<Set<String>, MutableSet<Set<String>>>()
        sccs.forEach { scc -> sccEdges[scc] = mutableSetOf() }

        for (scc in sccs) {
            for (fn in scc) {
                for (callee in getCallees(fn)) {
                    val calleeSCC = functionToSCC[callee]
                    if (calleeSCC != null && calleeSCC != scc) {
                        sccEdges[scc]!!.add(calleeSCC)
                    }
                }
            }
        }

        // Topological sort of SCCs using DFS
        val result = mutableListOf<Set<String>>()
        val visited = mutableSetOf<Set<String>>()

        fun dfs(scc: Set<String>) {
            if (scc in visited) return
            visited.add(scc)

            // Visit all dependencies (callees) first
            sccEdges[scc]?.forEach { calleeSCC ->
                dfs(calleeSCC)
            }

            result.add(scc)
        }

        sccs.forEach { dfs(it) }
        return result
    }

    /**
     * Finds all function names called within the given CFG.
     */
    private fun findCalledFunctions(cfg: ControlFlowGraph): Set<String> {
        val calledFunctions = mutableSetOf<String>()

        cfg.blocks.values.forEach { block ->
            block.irNodes.forEach { node ->
                if (node is IRFunctionCall) {
                    calledFunctions.add(node.name)
                }
            }
        }

        return calledFunctions
    }
}