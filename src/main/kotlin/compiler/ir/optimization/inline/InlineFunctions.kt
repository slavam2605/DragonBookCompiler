package compiler.ir.optimization.inline

import compiler.frontend.FrontendFunction
import compiler.frontend.FrontendFunctions
import compiler.frontend.FrontendFunctions.Companion.callGraph
import compiler.frontend.isNoInline
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.cfg.utils.hasFunctionCalls
import compiler.ir.cfg.utils.irNodesCount

class InlineFunctions(private val ffs: FrontendFunctions<SSAControlFlowGraph>) {
    private val inlinableFunctions = ffs.values
        .filter { isFunctionInlinable(it) }
        .associateByTo(mutableMapOf()) { it.name }

    fun run(): FrontendFunctions<SSAControlFlowGraph> {
        val processedFunctions = mutableMapOf<String, SSAControlFlowGraph>()

        val order = ffs.callGraph().getSCCsInTopologicalOrder()
        for (scc in order) {
            for (fnName in scc) {
                val cfg = processedFunctions[fnName] ?: ffs[fnName]!!.value
                val inlinedCfg = inlineFunctionCalls(fnName, cfg)
                processedFunctions[fnName] = inlinedCfg
                if (fnName in inlinableFunctions) {
                    val newFunction = inlinableFunctions[fnName]!!.map { inlinedCfg }
                    if (isFunctionInlinable(newFunction)) {
                        // Update inlinable functions map only if the new function is still inlinable
                        inlinableFunctions[fnName] = newFunction
                    }
                }
            }
        }
        return ffs.map { processedFunctions[it.name]!! }
    }

    private fun inlineFunctionCalls(functionName: String, cfg: SSAControlFlowGraph): SSAControlFlowGraph {
        if (!cfg.hasFunctionCalls { it.name in inlinableFunctions }) {
            return cfg
        }

        return InlineTransformer(inlinableFunctions, cfg, functionName).transform()
    }

    private fun isFunctionInlinable(fn: FrontendFunction<SSAControlFlowGraph>): Boolean {
        // Respect noinline annotation
        if (fn.isNoInline()) {
            return false
        }

        // Check size heuristic
        return fn.value.irNodesCount() <= 20
    }
}