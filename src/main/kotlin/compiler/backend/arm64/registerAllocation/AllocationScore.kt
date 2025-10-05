package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.MemoryLocation
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.StackLocation
import compiler.ir.IRVar

/**
 * Represents the score for allocating a register to a variable.
 *
 * @param score Numeric score where lower is better. Used for sorting colors during allocation.
 * @param isAvoided True if this color should be avoided when selecting preferences.
 *                  Avoided colors can still be allocated if they're the best available option,
 *                  but explicit/copy preferences pointing to avoided colors are skipped.
 */
data class AllocationScore(
    val score: Int,
    val isAvoided: Boolean
) {
    companion object {
        /**
         * Computes allocation score for assigning [register] to [irVar].
         *
         * Scoring strategy:
         * - Variables live across calls prefer callee-saved registers (lower score)
         * - Variables not live across calls prefer caller-saved registers (lower score)
         * - Stack locations always get high score (worst option)
         * - Caller-saved registers are marked as "avoided" for variables live across calls
         */
        fun score(irVar: IRVar, register: MemoryLocation, livenessInfo: LivenessInfo): AllocationScore {
            val isLiveAcrossCalls = irVar in livenessInfo.liveAcrossAnyCalls

            return when (register) {
                is X -> {
                    val isCalleeSaved = register in X.CalleeSaved
                    val score = if (isLiveAcrossCalls) {
                        // Prefer callee-saved for variables live across calls
                        if (isCalleeSaved) 0 else 1
                    } else {
                        // Prefer caller-saved for other variables
                        if (isCalleeSaved) 1 else 0
                    }
                    val isAvoided = isLiveAcrossCalls && !isCalleeSaved
                    AllocationScore(score, isAvoided)
                }
                is D -> {
                    val isCalleeSaved = register in D.CalleeSaved
                    val score = if (isLiveAcrossCalls) {
                        // Prefer callee-saved for variables live across calls
                        if (isCalleeSaved) 0 else 1
                    } else {
                        // Prefer caller-saved for other variables
                        if (isCalleeSaved) 1 else 0
                    }
                    val isAvoided = isLiveAcrossCalls && !isCalleeSaved
                    AllocationScore(score, isAvoided)
                }
                is StackLocation -> AllocationScore(100, false)
                else -> error("Unsupported memory location: $register")
            }
        }
    }
}
