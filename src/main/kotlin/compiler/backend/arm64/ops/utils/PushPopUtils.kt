package compiler.backend.arm64.ops.utils

import compiler.backend.arm64.*
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.instructions.Instruction
import compiler.backend.arm64.instructions.Ldp
import compiler.backend.arm64.instructions.Ldr
import compiler.backend.arm64.instructions.Stp
import compiler.backend.arm64.instructions.StpMode
import compiler.backend.arm64.instructions.Str

object PushPopUtils {
    fun fillPairs(
        regPairs: MutableList<Pair<Register, Register?>>,
        usedRegisters: Set<Register>,
        saved: Set<Register>
    ) {
        val regs = usedRegisters
            .filter { it in saved }
            .sortedBy { (it as? X)?.index ?: (it as D).index }

        // Create pairs of used callee-saved registers
        for (i in 0 until regs.size step 2) {
            if (i == regs.lastIndex) break
            regPairs.add(regs[i] to regs[i + 1])
        }
        if (regs.size % 2 == 1) regPairs.add(regs.last() to null)
    }

    fun createPopOps(regPairs: List<Pair<Register, Register?>>): List<Instruction> {
        if (regPairs.isEmpty()) return emptyList()

        val totalSize = regPairs.size * 16
        val result = mutableListOf<Instruction>()
        val reversed = regPairs.reversed()

        // All but last: SIGNED_OFFSET
        for (i in 0 until reversed.size - 1) {
            val (reg1, reg2) = reversed[i]
            val offset = (regPairs.size - 1 - i) * 16
            if (reg2 != null) {
                result.add(Ldp(reg1, reg2, SP, offset, StpMode.SIGNED_OFFSET))
            } else {
                result.add(Ldr(reg1, SP, offset, StpMode.SIGNED_OFFSET))
            }
        }

        // Last: POST_INDEXED with totalSize
        val (lastR1, lastR2) = reversed.last()
        if (lastR2 != null) {
            result.add(Ldp(lastR1, lastR2, SP, totalSize, StpMode.POST_INDEXED))
        } else {
            result.add(Ldr(lastR1, SP, totalSize, StpMode.POST_INDEXED))
        }

        return result
    }

    fun createPushOps(regPairs: List<Pair<Register, Register?>>): List<Instruction> {
        if (regPairs.isEmpty()) return emptyList()

        val totalSize = regPairs.size * 16
        val result = mutableListOf<Instruction>()

        // First instruction: PRE_INDEXED with -totalSize
        val (r1, r2) = regPairs[0]
        if (r2 != null) {
            result.add(Stp(r1, r2, SP, -totalSize, StpMode.PRE_INDEXED))
        } else {
            result.add(Str(r1, SP, -totalSize, StpMode.PRE_INDEXED))
        }

        // Rest: SIGNED_OFFSET with increasing offsets
        for (i in 1 until regPairs.size) {
            val (reg1, reg2) = regPairs[i]
            val offset = i * 16
            if (reg2 != null) {
                result.add(Stp(reg1, reg2, SP, offset, StpMode.SIGNED_OFFSET))
            } else {
                result.add(Str(reg1, SP, offset, StpMode.SIGNED_OFFSET))
            }
        }

        return result
    }
}