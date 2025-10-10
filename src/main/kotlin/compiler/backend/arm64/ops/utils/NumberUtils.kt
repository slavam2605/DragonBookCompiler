package compiler.backend.arm64.ops.utils

import compiler.backend.arm64.Adrp
import compiler.backend.arm64.FMov
import compiler.backend.arm64.FMovImm
import compiler.backend.arm64.Instruction
import compiler.backend.arm64.InstructionUtils
import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Ldr
import compiler.backend.arm64.Mov
import compiler.backend.arm64.MovBitPattern
import compiler.backend.arm64.MovK
import compiler.backend.arm64.MovN
import compiler.backend.arm64.MovZ
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.StpMode
import java.lang.Double.doubleToLongBits
import kotlin.ranges.contains

object NumberUtils {
    fun emitAssignConstantInt64(context: NativeCompilerContext, targetReg: X, value: Long) {
        val ops = createIntEmitOps(targetReg, value)
        context.ops.addAll(ops)
    }

    private fun createIntEmitOps(targetReg: X, value: Long): List<Instruction> {
        val ops = mutableListOf<Instruction>()

        if (value == 0L) {
            ops.add(Mov(targetReg, IntRegister.Xzr))
            return ops
        }
        if (value == -1L) {
            ops.add(MovN(targetReg, 0L, 0))
            return ops
        }

        if (InstructionUtils.isBitPattern(value)) {
            ops.add(MovBitPattern(targetReg, value))
            return ops
        }

        val parts = (0..3).map { (value ushr (16 * it)) and 0xFFFFL }
        val negParts = (0..3).map { (value.inv() ushr (16 * it)) and 0xFFFFL }
        val nonZeroPartsCount = parts.count { it != 0L }
        val nonZeroNegPartsCount = negParts.count { it != 0L }
        check(nonZeroPartsCount >= 1 && nonZeroNegPartsCount >= 1)

        val opsNeeded = minOf(nonZeroPartsCount, nonZeroNegPartsCount)
        if (opsNeeded > 2 && parts.toSet().size == 2) {
            val common = if (parts[0] == parts[1]) parts[0] else parts[2]
            val diffIndex = parts.indexOfFirst { it != common }
            val common4 = longFromSamePart(common)
            if (InstructionUtils.isBitPattern(common4)) {
                ops.add(MovBitPattern(targetReg, common4))
                ops.add(MovK(targetReg, parts[diffIndex], 16 * diffIndex))
                return ops
            }
        }

        if (opsNeeded > 3 && parts.toSet().size == 3) {
            val common = parts.find { part -> parts.count { it == part } == 2 }!!
            val diffIndices = parts.indices.filter { parts[it] != common }
            check(diffIndices.size == 2)
            val common4 = longFromSamePart(common)
            if (InstructionUtils.isBitPattern(common4)) {
                ops.add(MovBitPattern(targetReg, common4))
                diffIndices.forEach { diffIndex ->
                    ops.add(MovK(targetReg, parts[diffIndex], 16 * diffIndex))
                }
                return ops
            }
        }

        val useNeg = nonZeroNegPartsCount < nonZeroPartsCount
        val targetParts = if (useNeg) negParts else parts
        var isFirstOp = true
        targetParts.forEachIndexed { index, part ->
            if (part == 0L) return@forEachIndexed
            val opCtr = when {
                isFirstOp && useNeg -> ::MovN
                isFirstOp && !useNeg -> ::MovZ
                else -> ::MovK
            }
            val part = if (useNeg && !isFirstOp) parts[index] else part
            ops.add(opCtr(targetReg, part, 16 * index))
            isFirstOp = false
        }
        check(!isFirstOp)
        return ops
    }

    private fun longFromSamePart(part: Long) =
        (part shl 48) or (part shl 32) or (part shl 16) or part

    fun emitAssignConstantFloat64(context: NativeCompilerContext, targetReg: D, value: Double) {
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

        var isIntEmitUsed = false
        context.allocator.tempIntReg { reg ->
            val intOps = createIntEmitOps(reg, bits)
            // TODO why only two parts?
            if (intOps.size <= 2) {
                emitAssignConstantInt64(context, reg, bits)
                context.ops.add(FMov(targetReg, reg))
                isIntEmitUsed = true
            }
        }
        if (isIntEmitUsed) return

        val label = context.constPool.getConstant(value)
        context.allocator.tempIntReg { reg ->
            context.ops.add(Adrp(reg, "$label@PAGE"))
            context.ops.add(Ldr(targetReg, reg, "$label@PAGEOFF", StpMode.SIGNED_OFFSET))
        }
    }
}