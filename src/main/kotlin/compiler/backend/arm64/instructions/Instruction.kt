package compiler.backend.arm64.instructions

import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.Register
import compiler.backend.arm64.instructions.InstructionUtils.checkImm12Value
import compiler.backend.arm64.instructions.InstructionUtils.checkLabelName
import compiler.backend.arm64.instructions.InstructionUtils.checkShiftValue
import compiler.backend.arm64.instructions.InstructionUtils.checkUShortValue
import compiler.backend.arm64.instructions.InstructionUtils.stpModeAddress

abstract class Instruction(val opName: String) {
    open fun string(): String = opName

    override fun toString(): String = string()
}

// ------------ Floating-point instructions ------------

abstract class FloatThreeRegInstruction(
    opName: String, val dst: Register.D, val left: Register.D, val right: Register.D
) : Instruction(opName) {
    override fun string() = "$opName $dst, $left, $right"
}

abstract class FloatTwoRegInstruction(
    opName: String, val left: Register.D, val right: Register.D
) : Instruction(opName) {
    override fun string() = "$opName $left, $right"
}

abstract class FloatOneRegImmInstruction(
    opName: String, val dst: Register.D, val imm: Double
) : Instruction(opName) {
    override fun string() = "$opName $dst, $imm"
}

// ------------ Integer instructions ------------

abstract class IntRegInstruction(
    opName: String, val dst: IntRegister, val left: IntRegister, val right: IntRegister
) : Instruction(opName) {
    override fun string() = "$opName $dst, $left, $right"
}

abstract class IntTwoRegInstruction(
    opName: String, val left: IntRegister, val right: IntRegister
) : Instruction(opName) {
    override fun string() = "$opName $left, $right"
}

abstract class IntOneRegImmInstruction(
    opName: String, val left: IntRegister, val imm: Long, immKind: ImmKind
) : Instruction(opName) {
    init {
        immKind.checkImmValue(imm)
    }

    override fun string() = "$opName $left, $imm"
}

abstract class IntNoRegCFInstruction(
    opName: String, val dst: IntRegister.X, val cond: ConditionFlag
) : Instruction(opName) {
    override fun string() = "$opName $dst, $cond"
}

abstract class IntRegCFInstruction(
    opName: String, val dst: IntRegister, val left: IntRegister, val right: IntRegister, val cond: ConditionFlag
) : Instruction(opName) {
    override fun string() = "$opName $dst, $left, $right, $cond"
}

abstract class IntThreeRegInstruction(
    opName: String, val dst: IntRegister, val left: IntRegister, val right: IntRegister, val third: IntRegister
) : Instruction(opName) {
    override fun string() = "$opName $dst, $left, $right, $third"
}

abstract class IntRegShiftInstruction(
    opName: String, val dst: IntRegister, val left: IntRegister, val right: IntRegister,
    val shiftKind: ShiftKind?, val imm: Int?, vararg forbiddenShiftKinds: ShiftKind
) : Instruction(opName) {
    init {
        require(shiftKind !in forbiddenShiftKinds) { "$opName does not support shift kind $shiftKind" }
        if (shiftKind != null || imm != null) {
            require(shiftKind != null && imm != null) { "Both shiftKind and imm must be specified" }
            require(imm in 0..63) { "Invalid imm value: $imm" }
        }
    }

    override fun string() = "$opName $dst, $left, $right${if (shiftKind != null) ", $shiftKind $imm" else ""}"
}

abstract class IntOneRegShiftInstruction(
    opName: String, val dst: IntRegister, val src: IntRegister,
    val shiftKind: ShiftKind?, val imm: Int?, vararg forbiddenShiftKinds: ShiftKind
) : Instruction(opName) {
    init {
        require(shiftKind !in forbiddenShiftKinds) { "$opName does not support shift kind $shiftKind" }
        if (shiftKind != null || imm != null) {
            require(shiftKind != null && imm != null) { "Both shiftKind and imm must be specified" }
            require(imm in 0..63) { "Invalid imm value: $imm" }
        }
    }

    override fun string() = "$opName $dst, $src${if (shiftKind != null) ", $shiftKind $imm" else ""}"
}

abstract class IntOneRegImm16LslShiftInstruction(
    opName: String, val dst: IntRegister, val imm16: Int, val shift: Int
) : Instruction(opName) {
    init {
        checkUShortValue(imm16)
        checkShiftValue(shift)
    }

    override fun string() = "$opName $dst, $imm16, lsl $shift"
}

abstract class IntRegLabelInstruction(
    opName: String, val dst: IntRegister, val label: String
) : Instruction(opName) {
    override fun string() = "$opName $dst, $label"
}

abstract class IntTwoRegImmInstruction(
    opName: String, val dst: IntRegister, val left: IntRegister, val imm: Long, immKind: ImmKind
) : Instruction(opName) {
    init {
        immKind.checkImmValue(imm)
    }

    override fun string() = "$opName $dst, $left, $imm"
}

// ------------ Mixed register instructions ------------

abstract class FloatAnyRegInstruction(
    opName: String, val dst: Register.D, val left: Register
) : Instruction(opName) {
    override fun string() = "$opName $dst, $left"
}

abstract class RegStoreLoadInstruction(
    opName: String, val reg: Register, val address: IntRegister, val offset: Any, val mode: StpMode
) : Instruction(opName) {
    init {
        require(offset is Int || offset is String)
    }

    override fun string() = "$opName $reg, ${stpModeAddress(address, offset, mode)}"
}

abstract class TwoRegStoreLoadInstruction(
    opName: String, val reg1: Register, val reg2: Register, val address: IntRegister, val offset: Any, val mode: StpMode
) : Instruction(opName) {
    init {
        require(offset is Int || offset is String)
    }

    override fun string() = "$opName $reg1, $reg2, ${stpModeAddress(address, offset, mode)}"
}

abstract class FloatIntRegInstruction(
    opName: String, val dst: Register.D, val src: IntRegister.X
) : Instruction(opName) {
    override fun string() = "$opName $dst, $src"
}

abstract class IntFloatRegInstruction(
    opName: String, val dst: IntRegister.X, val src: Register.D
) : Instruction(opName) {
    override fun string() = "$opName $dst, $src"
}

// ------------ Miscellaneous instructions ------------

abstract class LabelInstruction(opName: String, val label: String) : Instruction(opName) {
    init {
        checkLabelName(label)
    }

    override fun string(): String = "$opName $label"
}

abstract class CondLabelInstruction(
    opName: String, val cond: ConditionFlag, val label: String
) : Instruction(opName) {
    init {
        checkLabelName(label)
    }

    override fun string(): String = "$opName.$cond $label"
}

class CustomText(val text: String) : Instruction("") {
    override fun string(): String = text
}

class Label(val name: String) : Instruction("") {
    init { checkLabelName(name) }
    override fun string(): String = "$name:"
}

enum class StpMode { SIGNED_OFFSET, PRE_INDEXED, POST_INDEXED }

enum class ImmKind {
    IMM12, SHIFT, BIT_PATTERN;

    fun checkImmValue(value: Long) = when (this) {
        IMM12 -> checkImm12Value(value)
        SHIFT -> require(value in 0..63) { "Invalid value: $value, must be in range 0..63" }
        BIT_PATTERN -> require(InstructionUtils.isBitPattern(value)) { "Invalid value: $value" }
    }
}

enum class ShiftKind {
    LSL, LSR, ASR, ROR;

    override fun toString() = name.lowercase()
}

enum class ConditionFlag {
    EQ, NE, CS, CC, MI, PL, VS, VC, HI, LS, GE, LT, GT, LE, AL;

    fun invert(): ConditionFlag = when (this) {
        EQ -> NE
        NE -> EQ
        CS -> CC
        CC -> CS
        MI -> PL
        PL -> MI
        VS -> VC
        VC -> VS
        HI -> LS
        LS -> HI
        GE -> LT
        LT -> GE
        GT -> LE
        LE -> GT
        AL -> error("AL is not invertible")
    }

    override fun toString() = name.lowercase()
}