package compiler.frontend

import compiler.ir.IRVar

class FrontendFunctions<T> {
    private val functions = mutableMapOf<String, FrontendFunction<T>>()

    val values: List<FrontendFunction<T>>
        get() = functions.values.toList()

    fun addFunction(function: FrontendFunction<T>) {
        functions[function.name] = function
    }

    operator fun get(name: String): FrontendFunction<T>? = functions[name]

    operator fun contains(name: String): Boolean = name in functions

    fun <R> map(transform: (FrontendFunction<T>) -> R): FrontendFunctions<R> {
        return FrontendFunctions<R>().also { newFunctions ->
            functions.forEach { (name, function) ->
                newFunctions.addFunction(
                    FrontendFunction(
                        name = name,
                        parameters = function.parameters,
                        hasReturnType = function.hasReturnType,
                        endLocation = function.endLocation,
                        value = transform(function)
                    )
                )
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
}

class FrontendFunction<T>(
    val name: String,
    val parameters: List<IRVar>,
    val hasReturnType: Boolean,
    val endLocation: SourceLocation,
    val value: T
)