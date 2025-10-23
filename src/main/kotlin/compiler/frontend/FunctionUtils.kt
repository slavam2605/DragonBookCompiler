package compiler.frontend

import compiler.frontend.FrontendFunctions.Companion.callGraph
import compiler.ir.cfg.ControlFlowGraph

private object FunctionUtils {
    const val NO_INLINE_ANNOTATION = "noinline"
    const val PURE_ANNOTATION = "pure"
    const val IMPURE_ANNOTATION = "impure"

    fun isPure(ffs: FrontendFunctions<out ControlFlowGraph>, name: String, visited: MutableSet<String>): Boolean {
        // If in cycle => ignore, doesn't change purity
        if (name in visited) return true

        // External functions are considered non-pure
        val function = ffs[name] ?: return false

        // Explicitly annotated functions are considered pure
        if (function.hasAnnotation(PURE_ANNOTATION)) return true

        // Explicitly annotated functions are considered impure
        if (function.hasAnnotation(IMPURE_ANNOTATION)) return false

        // Mark as visited before recursing to prevent infinite loops
        visited.add(name)

        // The function is pure if all functions that it calls are pure
        val result = ffs.callGraph().getCallees(name)
            .all { calleeName -> isPure(ffs, calleeName, visited) }

        // Remove from the visited set for other branches
        visited.remove(name)

        return result
    }
}

fun FrontendFunction<*>.isNoInline(): Boolean =
    hasAnnotation(FunctionUtils.NO_INLINE_ANNOTATION)

fun FrontendFunctions<out ControlFlowGraph>.isPure(name: String): Boolean =
    FunctionUtils.isPure(this, name, mutableSetOf())