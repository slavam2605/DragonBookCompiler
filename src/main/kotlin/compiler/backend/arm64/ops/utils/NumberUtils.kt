package compiler.backend.arm64.ops.utils

import compiler.backend.arm64.Adrp
import compiler.backend.arm64.FMov
import compiler.backend.arm64.FMovImm
import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Ldr
import compiler.backend.arm64.Mov
import compiler.backend.arm64.MovK
import compiler.backend.arm64.MovZ
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.StpMode
import java.lang.Double.doubleToLongBits
import kotlin.ranges.contains

object NumberUtils {
    fun emitAssignConstantInt64(context: NativeCompilerContext, targetReg: X, value: Long) {
        // TODO add support for cases with movn
        if (value == 0L) {
            context.ops.add(Mov(targetReg, IntRegister.Xzr))
            return
        }

        val parts = (0..3).map { (value ushr (16 * it)) and 0xFFFFL }

        var isFirstOp = true
        parts.forEachIndexed { index, part ->
            if (part == 0L) return@forEachIndexed
            val opCtr = if (isFirstOp) ::MovZ else ::MovK
            context.ops.add(opCtr(targetReg, part, 16 * index))
            isFirstOp = false
        }
        check(!isFirstOp)
    }

    fun emitAssignConstantFloat64(context: NativeCompilerContext, targetReg: D, value: Double) {
        // TODO support mov/movk and fmov d<n>, x<m>

        if (value == 0.0) {
            context.ops.add(FMov(targetReg, IntRegister.Xzr))
            return
        }

        // Use `fmov dX, imm8` if possible
        val bits = doubleToLongBits(value)
        val exp = ((bits ushr 52) and 0x7FFL) - 1023
        val frac = bits and ((1L shl 52) - 1)
        if (exp in -3..4 && (frac and ((1L shl 48) - 1)) == 0L) {
            context.ops.add(FMovImm(targetReg, value))
            return
        }

        // TODO why only two parts?
        val parts = (0..3).map { (bits ushr (16 * it)) and 0xFFFFL }
        if (parts.count { it != 0L } <= 2) {
            context.allocator.tempIntReg { reg ->
                emitAssignConstantInt64(context, reg, bits)
                context.ops.add(FMov(targetReg, reg))
            }
            return
        }

        val label = context.constPool.getConstant(value)
        context.allocator.tempIntReg { reg ->
            context.ops.add(Adrp(reg, "$label@PAGE"))
            context.ops.add(Ldr(targetReg, reg, "$label@PAGEOFF", StpMode.SIGNED_OFFSET))
        }
    }
}