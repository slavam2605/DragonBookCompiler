package compiler.frontend

/**
 * A symbol table with support for nested scopes.
 * Each scope can access symbols from its parent scopes,
 * but parent scopes cannot access symbols from child scopes.
 *
 * @param T the type of values stored for each symbol name
 */
class SymbolTable<T> {
    private val scopes = mutableListOf<MutableMap<String, T>>()

    init {
        // Initialize with global scope
        pushScope()
    }

    /**
     * Push a new scope onto the stack
     */
    fun pushScope() {
        scopes.add(mutableMapOf())
    }

    /**
     * Pop the current scope from the stack
     * @throws IllegalStateException if trying to pop the global scope
     */
    fun popScope() {
        if (scopes.size <= 1) {
            throw IllegalStateException("Cannot pop global scope")
        }
        scopes.removeAt(scopes.lastIndex)
    }

    /**
     * Run a block of code within a new scope
     */
    fun <T> withScope(block: () -> T): T {
        pushScope()
        try {
            return block()
        } finally {
            popScope()
        }
    }

    /**
     * Define a symbol in the current scope
     * @param name The name of the symbol
     * @param value The value of type T to associate with the name
     * @return The previous value from the current scope level, in case of a redefinition
     */
    fun define(name: String, value: T): T? {
        return scopes.last().put(name, value)
    }

    /**
     * Look up a symbol in the current scope and all parent scopes
     * @param name The name of the symbol to look up
     * @return The value of type T associated with the name, or null if not found
     */
    fun lookup(name: String): T? {
        // Search from innermost to outermost scope
        for (i in scopes.lastIndex downTo 0) {
            val scope = scopes[i]
            val value = scope[name]
            if (value != null) {
                return value
            }
        }
        return null
    }

    /**
     * Check if a symbol is defined in the current scope
     * @param name The name of the symbol to check
     * @return True if the symbol is defined in the current scope, false otherwise
     */
    fun isDefined(name: String): Boolean {
        return scopes.last().containsKey(name)
    }

    /**
     * Get the current scope depth (number of nested scopes)
     * @return The current scope depth
     */
    fun scopeDepth(): Int {
        return scopes.size
    }
}