package compiler.backend.arm64.ops.integers

import compiler.backend.arm64.*
import compiler.backend.arm64.ops.utils.NumberUtils
import utils.isPowerOfTwo
import kotlin.math.absoluteValue

object IntConstantDivision {
    fun tryEmitConstantDivision(context: NativeCompilerContext, dst: IntRegister.X,
                                dividend: IntRegister.X, divisor: Long, isMod: Boolean): Boolean {
        if (tryEmitPowerOfTwoDivision(context, dst, dividend, divisor, isMod)) return true
        if (MagicIntConstantDivision.tryEmitMagicDivision(context, dst, dividend, divisor, isMod)) return true
        return false
    }

    fun tryEmitPowerOfTwoDivision(context: NativeCompilerContext, dst: IntRegister.X,
                                  dividend: IntRegister.X, divisor: Long, isMod: Boolean): Boolean {
        if (!divisor.isPowerOfTwo()) return false

        val ops = context.ops
        val absDivisor = divisor.absoluteValue
        val power = absDivisor.countTrailingZeroBits()

        if (isMod) {
            emitModPowerOfTwo(context, absDivisor, ops, dividend, dst)
        } else {
            emitDivPowerOfTwo(context, absDivisor, ops, dividend, dst, divisor, power)
        }
        return true
    }

    private fun emitModPowerOfTwo(context: NativeCompilerContext, absDivisor: Long, ops: MutableList<Instruction>,
                                  dividend: IntRegister.X, dst: IntRegister.X) {
        if (absDivisor == Long.MIN_VALUE) {
            // TODO rewrite with tempIntReg: RegHandle<X>: if dst != dividend, temp = RegHandle(dst) {}
            context.allocator.tempIntReg { temp ->
                NumberUtils.emitAssignConstantInt64(context, temp, Long.MIN_VALUE)
                ops.add(Cmp(dividend, temp))
                ops.add(CSet(temp, ConditionFlag.EQ))
                ops.add(Sub(dst, dividend, temp, ShiftKind.LSL, 63))
            }
        }

        if (absDivisor == 1L) {
            NumberUtils.emitAssignConstantInt64(context, dst, 0)
            return
        }

        // TODO temp may be `dividend` if it is dead after the op (and `dividend` != `dst`)
        context.allocator.tempIntReg { temp ->
            if (dst == dividend) {
                ops.add(Mov(temp, dividend))
                ops.add(Negs(dst, dividend))
                ops.add(AndImm(temp, temp, absDivisor - 1))
            } else {
                ops.add(Negs(dst, dividend))
                ops.add(AndImm(temp, dividend, absDivisor - 1))
            }
            ops.add(AndImm(dst, dst, absDivisor - 1))
            ops.add(CSNeg(dst, temp, dst, ConditionFlag.MI))
        }
    }

    private fun emitDivPowerOfTwo(context: NativeCompilerContext, absDivisor: Long, ops: MutableList<Instruction>,
                                  dividend: IntRegister.X, dst: IntRegister.X, divisor: Long, power: Int) {
        if (divisor == Long.MIN_VALUE) {
            // TODO rewrite with tempIntReg: RegHandle<X>: if dst != dividend, temp = RegHandle(dst) {}
            context.allocator.tempIntReg { temp ->
                NumberUtils.emitAssignConstantInt64(context, temp, Long.MIN_VALUE)
                ops.add(Cmp(dividend, temp))
                ops.add(CSet(dst, ConditionFlag.EQ))
            }
        }

        context.allocator.tempIntReg { temp ->
            // TODO for small integers we can use add (imm12 + shift)
            NumberUtils.emitAssignConstantInt64(context, temp, absDivisor - 1)
            ops.add(Add(temp, dividend, temp))
            ops.add(CmpImm(dividend, 0))
            ops.add(CSel(dst, temp, dividend, ConditionFlag.LT))
            if (divisor > 0) {
                ops.add(AsrImm(dst, dst, power))
            } else {
                ops.add(Neg(dst, dst, ShiftKind.ASR, power))
            }
        }
    }
}