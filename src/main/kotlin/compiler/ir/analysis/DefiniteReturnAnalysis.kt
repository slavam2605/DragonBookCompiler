package compiler.ir.analysis

import compiler.frontend.CompilationFailed
import compiler.frontend.FrontendFunction
import compiler.frontend.MissingReturnException
import compiler.ir.IRReturn
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.reachableFrom

/**
 * Checks that for functions with an explicit return type, every execution path ends with a return statement.
 * Functions without a return type are ignored.
 */
class DefiniteReturnAnalysis(
    private val cfg: ControlFlowGraph,
    private val function: FrontendFunction<*>
) {
    fun run() {
        // Ignore functions without a return type
        if (!function.hasReturnType) return

        // Find a block with the implicit return
        val implicitReturnBlock = cfg.blocks.filter { (_, block) ->
            val last = block.irNodes.lastOrNull()
            last is IRReturn && last.value == null
        }.keys.singleOrNull()
            ?: error("Multiple implicit return blocks in function ${function.name}")

        val reachable = cfg.reachableFrom(cfg.root)
        if (implicitReturnBlock in reachable) {
            // Implicit return is reachable => missing explicit return on some path
            throw CompilationFailed(listOf(
                MissingReturnException(function.endLocation, function.name)
            ))
        }
    }
}
