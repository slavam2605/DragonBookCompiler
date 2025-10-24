package compiler.ir

/**
 * Names of compiler intrinsic functions.
 * These are special functions recognized by the compiler with specific semantics.
 */
object Intrinsics {
    /**
     * Heap memory allocation function.
     * Usage: `malloc(size_in_bytes) as type*`
     * Allocates the specified number of bytes on the heap.
     * Parameter `int size_in_bytes` - number of bytes to allocate.
     * Returns a pointer to allocated memory.
     */
    const val MALLOC = "malloc"

    /**
     * Heap memory deallocation function.
     * Usage: `free(ptr)`
     * Frees memory previously allocated with malloc.
     */
    const val FREE = "free"

    /**
     * Undefined value intrinsic.
     * Usage: `undef(value)`
     * Returns an undefined value of the same type as the argument.
     */
    const val UNDEF = "undef"
}
