package compiler.backend.arm64.ops.integers

import compiler.backend.arm64.instructions.Add
import compiler.backend.arm64.instructions.AsrImm
import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.instructions.MSub
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.instructions.SMulh
import compiler.backend.arm64.instructions.ShiftKind
import compiler.backend.arm64.instructions.Sub
import compiler.backend.arm64.ops.utils.NumberUtils
import utils.absIsPowerOfTwo
import kotlin.math.absoluteValue

internal object MagicIntConstantDivision {
    internal data class Magic(val magic: Long, val shift: Int)

    fun tryEmitMagicDivision(context: NativeCompilerContext, dst: IntRegister.X,
                             dividend: IntRegister.X, divisor: Long, isMod: Boolean): Boolean {
        if (divisor == 0L || divisor.absIsPowerOfTwo()) return false

        val m = getMagic(divisor)
        context.allocator.tempIntReg { temp ->
            NumberUtils.emitAssignConstantInt64(context, temp, m.magic)
            context.ops.add(SMulh(temp, dividend, temp))
            if (divisor > 0 && m.magic < 0) {
                context.ops.add(Add(temp, temp, dividend))
            }
            if (divisor < 0 && m.magic > 0) {
                context.ops.add(Sub(temp, temp, dividend))
            }
            if (m.shift > 0) {
                context.ops.add(AsrImm(temp, temp, m.shift))
            }
            if (!isMod) {
                context.ops.add(Add(dst, temp, temp, ShiftKind.LSR, 63))
            } else {
                context.ops.add(Add(temp, temp, temp, ShiftKind.LSR, 63))
                context.allocator.tempIntReg { temp2 ->
                    val tReg = if (dst == dividend) temp2 else dst
                    NumberUtils.emitAssignConstantInt64(context, tReg, divisor)
                    context.ops.add(MSub(dst, temp, tReg, dividend))
                }
            }
        }
        return true
    }

    /**
     * From Hacker's Delight, Second Edition: Figure 10-1.
     */
    internal fun getMagic(d: Long): Magic {
        val two63 = 1UL shl 63
        val ad = d.absoluteValue.toULong()
        val t = two63 + (d.toULong() shr 63)
        val anc = t - 1UL - t % ad
        var p = 63
        var q1 = two63 / anc
        var r1 = two63 - q1 * anc
        var q2 = two63 / ad
        var r2 = two63 - q2 * ad
        var delta: ULong
        do {
            p += 1
            q1 = 2UL * q1
            r1 = 2UL * r1
            if (r1 >= anc) {
                q1 += 1UL
                r1 -= anc
            }
            q2 = 2UL * q2
            r2 = 2UL * r2
            if (r2 >= ad) {
                q2 += 1UL
                r2 -= ad
            }
            delta = ad - r2
        } while (q1 < delta || (q1 == delta && r1 == 0UL))
        var magic = (q2 + 1UL).toLong()
        if (d < 0) magic = -magic
        val shift = p - 64
        return Magic(magic, shift)
    }
}