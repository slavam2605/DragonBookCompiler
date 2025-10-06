package compiler.backend.arm64.registerAllocation

import compiler.ir.IRFunctionCall
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRVar

/**
 * Holds liveness analysis results for a function.
 * This data is used by the register allocator to make better allocation decisions.
 *
 * Note: This class is type-agnostic and contains liveness information for all variable types.
 * Type-specific filtering is performed by consumers as needed.
 */
class LivenessInfo(
    /**
     * Maps each function call to the set of variables whose live ranges span the call.
     * These are variables in the intersection of liveIn and liveOut at the call site.
     */
    val liveAtCalls: Map<IRFunctionCall, Set<IRVar>>,

    /**
     * Maps each conditional jump to the set of variables which are live after the jump.
     */
    private val liveAfterBranch: Map<IRJumpIfTrue, Set<IRVar>>
) {
    /**
     * Set of all variables that are live across at least one function call.
     * Computed by taking the union of all liveAtCalls values.
     */
    val liveAcrossAnyCalls: Set<IRVar> = liveAtCalls.values.flatten().toSet()

    fun isLiveAfterBranch(irVar: IRVar, jump: IRJumpIfTrue): Boolean {
        return irVar in (liveAfterBranch[jump] ?: return false)
    }
}
