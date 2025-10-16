package compiler.frontend

import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class FrontendFunctions<T> {
    private val functions = mutableMapOf<String, FrontendFunction<T>>()
    private var callGraph: CallGraph<T>? = null

    val values: List<FrontendFunction<T>>
        get() = functions.values.toList()

    fun addFunction(function: FrontendFunction<T>) {
        functions[function.name] = function
    }

    operator fun get(name: String): FrontendFunction<T>? = functions[name]

    operator fun contains(name: String): Boolean = name in functions

    fun <R> map(transform: (FrontendFunction<T>) -> R): FrontendFunctions<R> {
        return FrontendFunctions<R>().also { newFunctions ->
            functions.forEach { (_, function) ->
                newFunctions.addFunction(function.map(transform))
            }
        }
    }

    fun forEach(action: (FrontendFunction<T>) -> Unit) {
        functions.forEach { (_, function) -> action(function) }
    }

    fun print(printBlock: (T) -> Unit) {
        functions.forEach { (name, function) ->
            println("Function $name:")
            printBlock(function.value)
            println()
        }
    }

    companion object {
        fun <T> FrontendFunctions<T>.callGraph(cfgGetter: (T) -> ControlFlowGraph): CallGraph<T> {
            if (callGraph != null) return callGraph!!
            return CallGraph(this, cfgGetter).also {
                callGraph = it
            }
        }

        fun <T : ControlFlowGraph> FrontendFunctions<T>.callGraph(): CallGraph<T> = callGraph { it }
    }
}

class FrontendFunction<T>(
    val name: String,
    val parameters: List<IRVar>,
    val hasReturnType: Boolean,
    val endLocation: SourceLocation,
    val annotations: Set<String>,
    val value: T
) {
    fun hasAnnotation(annotation: String): Boolean = annotation in annotations

    fun <R> map(transformer: (FrontendFunction<T>) -> R): FrontendFunction<R> = FrontendFunction(
        name = name,
        parameters = parameters,
        hasReturnType = hasReturnType,
        endLocation = endLocation,
        annotations = annotations,
        value = transformer(this)
    )
}