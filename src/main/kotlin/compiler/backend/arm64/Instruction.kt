package compiler.backend.arm64

import compiler.backend.arm64.InstructionUtils.checkImm12Value
import compiler.backend.arm64.InstructionUtils.checkLabelName
import compiler.backend.arm64.InstructionUtils.checkShiftValue
import compiler.backend.arm64.InstructionUtils.checkUShortValue
import compiler.backend.arm64.InstructionUtils.stpModeAddress
import kotlin.ranges.contains

sealed class Instruction {
    abstract fun string(): String

    override fun toString(): String = string()
}

class CustomText(val text: String) : Instruction() {
    override fun string(): String = text
}

class Label(val name: String) : Instruction() {
    init { checkLabelName(name) }
    override fun string(): String = "$name:"
}

class FAdd(val dst: Register.D, val left: Register.D, val right: Register.D) : Instruction() {
    override fun string(): String = "fadd $dst, $left, $right"
}

class FSub(val dst: Register.D, val left: Register.D, val right: Register.D) : Instruction() {
    override fun string(): String = "fsub $dst, $left, $right"
}

class FMul(val dst: Register.D, val left: Register.D, val right: Register.D) : Instruction() {
    override fun string(): String = "fmul $dst, $left, $right"
}

class FDiv(val dst: Register.D, val left: Register.D, val right: Register.D) : Instruction() {
    override fun string(): String = "fdiv $dst, $left, $right"
}

class Add(val dst: IntRegister.X, val left: IntRegister.X, val right: IntRegister.X) : Instruction() {
    override fun string(): String = "add $dst, $left, $right"
}

class Sub(val dst: IntRegister.X, val left: IntRegister.X, val right: IntRegister.X) : Instruction() {
    override fun string(): String = "sub $dst, $left, $right"
}

class SubImm(val dst: IntRegister, val left: IntRegister, val imm12: Int) : Instruction() {
    init { checkImm12Value(imm12) }
    override fun string(): String = "sub $dst, $left, $imm12"
}

class Mul(val dst: IntRegister.X, val left: IntRegister.X, val right: IntRegister.X) : Instruction() {
    override fun string(): String = "mul $dst, $left, $right"
}

/** `dst = minuend - left * right` */
class MSub(val dst: IntRegister.X, val left: IntRegister.X, val right: IntRegister.X, val minuend: IntRegister.X) : Instruction() {
    override fun string(): String = "msub $dst, $left, $right, $minuend"
}

class SDiv(val dst: IntRegister.X, val left: IntRegister.X, val right: IntRegister.X) : Instruction() {
    override fun string(): String = "sdiv $dst, $left, $right"
}

class Cmp(val left: IntRegister.X, val right: IntRegister.X) : Instruction() {
    override fun string(): String = "cmp $left, $right"
}

class FCmp(val left: Register.D, val right: Register.D) : Instruction() {
    override fun string(): String = "fcmp $left, $right"
}

class CmpImm(val left: IntRegister.X, val imm12: Int) : Instruction() {
    init { checkImm12Value(imm12) }
    override fun string(): String = "cmp $left, $imm12"
}

class CSet(val dst: IntRegister.X, val cond: ConditionFlag) : Instruction() {
    override fun string(): String = "cset $dst, $cond"
}

class FMov(val dst: Register.D, val from: Register) : Instruction() {
    override fun string(): String = "fmov $dst, $from"
}

class FMovImm(val dst: Register.D, val imm8: Double) : Instruction() {
    override fun string(): String = "fmov $dst, $imm8"
}

class Mov(val dst: IntRegister, val from: IntRegister) : Instruction() {
    override fun string(): String = "mov $dst, $from"
}

class MovZ(val dst: IntRegister.X, val value: Long, val shift: Int = 0) : Instruction() {
    init {
        checkUShortValue(value)
        checkShiftValue(shift)
    }
    override fun string(): String = "movz $dst, $value" + if (shift != 0) ", lsl $shift" else ""
}

class MovK(val dst: IntRegister.X, val value: Long, val shift: Int) : Instruction() {
    init {
        checkUShortValue(value)
        checkShiftValue(shift)
    }
    override fun string(): String = "movk $dst, $value" + if (shift != 0) ", lsl $shift" else ""
}

class Adrp(val reg: IntRegister.X, val label: String) : Instruction() {
    override fun string(): String = "adrp $reg, $label"
}

class Str(val reg: Register, val address: IntRegister, val offset: Any, val mode: StpMode) : Instruction() {
    override fun string(): String = "str $reg, ${stpModeAddress(address, offset, mode)}"
}

class Ldr(val dst: Register, val address: IntRegister, val offset: Any, val mode: StpMode) : Instruction() {
    override fun string(): String = "ldr $dst, ${stpModeAddress(address, offset, mode)}"
}

class Stp(val first: Register, val second: Register, val address: IntRegister,
          val offset: Any, val mode: StpMode) : Instruction() {
    override fun string(): String = "stp $first, $second, ${stpModeAddress(address, offset, mode)}"
}

class Ldp(val first: Register, val second: Register, val address: IntRegister,
          val offset: Any, val mode: StpMode) : Instruction() {
    override fun string(): String = "ldp $first, $second, ${stpModeAddress(address, offset, mode)}"
}

class BL(val label: String) : Instruction() {
    override fun string(): String = "bl $label"
}

class BCond(val cond: ConditionFlag, val label: String) : Instruction() {
    override fun string(): String = "b.$cond $label"
}

class B(val label: String) : Instruction() {
    override fun string(): String = "b $label"
}

object Ret : Instruction() {
    override fun string(): String = "ret"
}

class Scvtf(val dst: Register.D, val src: IntRegister.X) : Instruction() {
    override fun string(): String = "scvtf $dst, $src"
}

enum class StpMode { SIGNED_OFFSET, PRE_INDEXED, POST_INDEXED }

enum class ConditionFlag {
    EQ, NE, CS, CC, MI, PL, VS, VC, HI, LS, GE, LT, GT, LE, AL;

    override fun toString() = name.lowercase()
}

private object InstructionUtils {
    private val AllowedShiftValues = setOf(0, 16, 32, 48)
    private val LabelNameRegex = "^[a-zA-Z0-9_.]+$".toRegex()

    fun checkLabelName(name: String) {
        require(name.matches(LabelNameRegex)) { "Invalid label name: $name" }
    }

    fun checkImm12Value(value: Int) {
        require(value in 0..4095) { "Invalid value: $value, must be in range 0..4095" }
    }

    fun checkUShortValue(value: Long) {
        require(value in 0..0xFFFF) { "Invalid value: $value" }
    }

    fun checkShiftValue(value: Int) {
        require(value in AllowedShiftValues) { "Invalid shift value: $value" }
    }

    fun stpModeAddress(address: IntRegister, offset: Any, mode: StpMode): String {
        val offsetString = when (offset) {
            is Int -> "#$offset"
            is String -> offset
            else -> error("Unsupported offset type: ${offset::class.simpleName}")
        }
        return "[$address" + when (mode) {
            StpMode.SIGNED_OFFSET -> ", $offsetString]"
            StpMode.PRE_INDEXED -> ", $offsetString]!"
            StpMode.POST_INDEXED -> "], $offsetString"
        }
    }
}