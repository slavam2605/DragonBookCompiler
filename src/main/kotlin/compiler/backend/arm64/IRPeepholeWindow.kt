package compiler.backend.arm64

import compiler.ir.IRNode

/**
 * Provides a sliding view over a sequence of IR nodes for peephole optimization.
 * Allows looking ahead/behind and moving through the IR sequence.
 */
class IRPeepholeWindow(private val irNodes: List<IRNode>) {
    private var currentIndex: Int = 0

    /**
     * Returns the current IRNode, or null if out of bounds
     */
    val current: IRNode?
        get() = irNodes.getOrNull(currentIndex)

    /**
     * Looks ahead (positive) or behind (negative) by [offset] positions
     * relative to the current position
     */
    fun peek(offset: Int): IRNode?
        = irNodes.getOrNull(currentIndex + offset)

    /**
     * Moves the window by [shift] positions (can be negative to move backward)
     */
    fun move(shift: Int = 1) {
        currentIndex += shift
    }

    /**
     * Checks if we can still read from the current position
     */
    val hasNext: Boolean
        get() = currentIndex < irNodes.size
}
