package compiler.backend.arm64.ops.utils

import compiler.backend.arm64.*
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register.D

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

    fun createPopOps(regPairs: List<Pair<Register, Register?>>): List<Instruction> =
        regPairs.reversed().map { (r1, r2) ->
            if (r2 != null) {
                Ldp(r1, r2, SP, 16, StpMode.POST_INDEXED)
            } else {
                Ldr(r1, SP, 16, StpMode.POST_INDEXED)
            }
        }

    fun createPushOps(regPairs: List<Pair<Register, Register?>>): List<Instruction> =
        regPairs.map { (r1, r2) ->
            if (r2 != null) {
                Stp(r1, r2, SP, -16, StpMode.PRE_INDEXED)
            } else {
                Str(r1, SP, -16, StpMode.PRE_INDEXED)
            }
        }
}