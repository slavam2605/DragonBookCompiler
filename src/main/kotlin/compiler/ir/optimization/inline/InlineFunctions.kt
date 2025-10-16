package compiler.ir.optimization.inline

import compiler.frontend.FrontendFunction
import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.cfg.utils.hasFunctionCalls
import compiler.ir.cfg.utils.irNodesCount

class InlineFunctions(private val ffs: FrontendFunctions<SSAControlFlowGraph>) {
    private val inlinableFunctions = ffs.values
        .filter { isFunctionInlinable(it) }
        .associateBy { it.name }

    fun run(): FrontendFunctions<SSAControlFlowGraph> {
        return ffs.map { inlineFunctionCalls(it.value) }
    }

    private fun inlineFunctionCalls(cfg: SSAControlFlowGraph): SSAControlFlowGraph {
        if (!cfg.hasFunctionCalls { it.name in inlinableFunctions }) {
            return cfg
        }

        return InlineTransformer(inlinableFunctions, cfg).transform()
    }

    private fun isFunctionInlinable(fn: FrontendFunction<SSAControlFlowGraph>): Boolean {
        return fn.value.irNodesCount() <= 20
    }
}