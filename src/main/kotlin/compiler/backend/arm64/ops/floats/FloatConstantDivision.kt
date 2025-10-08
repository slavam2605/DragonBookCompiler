package compiler.backend.arm64.ops.floats

import compiler.backend.arm64.FMul
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register
import compiler.backend.arm64.ops.utils.NumberUtils

object FloatConstantDivision {
    /**
     * Emits floating point division with multiplication if divisor is a power of two.
     * @return true if emitted, false otherwise
     */
    fun tryEmitConstantDivision(context: NativeCompilerContext, dst: Register.D,
                                dividend: Register.D, divisor: Double): Boolean {
        val reciprocal = getReciprocal(divisor) ?: return false
        context.allocator.tempFloatReg { temp ->
            NumberUtils.emitAssignConstantFloat64(context, temp, reciprocal)
            context.ops.add(FMul(dst, dividend, temp))
        }
        return true
    }

    internal fun getReciprocal(c: Double): Double? {
        // Step 1: C must be finite and nonzero (reject ±0, NaN, ±∞).
        if (c == 0.0 || c.isInfinite()) return null

        // Step 2: C must be an exact power of two (±2^k).
        if (!isExactPowerOfTwo(c)) return null

        // Step 3: R = 1.0/C must be finite in the same format.
        val r = 1.0 / c
        if (r.isInfinite()) return null

        return r
    }

    /**
     * IEEE-754 binary64 power-of-two test (accepts normals with zero fractions,
     * or denormalized floats with a single 1-bit).
     */
    private fun isExactPowerOfTwo(c: Double): Boolean {
        val bits = c.toBits()
        val exp  = ((bits ushr 52) and 0x7FFL).toInt()
        val frac = bits and ((1L shl 52) - 1L)
        return if (exp != 0) {
            // normal: the fraction must be exactly 0 (i.e., ±1.0 * 2^(exp-bias))
            frac == 0L
        } else {
            // denormalized: the fraction must be a power-of-two (exactly one bit set)
            frac != 0L && (frac and (frac - 1L)) == 0L
        }
    }
}
