package compiler.backend.arm64.instructions

import compiler.backend.arm64.IntRegister
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.Register
import compiler.backend.arm64.Register.D

class FAdd(dst: D, left: D, right: D)
    : FloatThreeRegInstruction("fadd", dst, left, right)

class FSub(dst: D, left: D, right: D)
    : FloatThreeRegInstruction("fsub", dst, left, right)

class FMul(dst: D, left: D, right: D)
    : FloatThreeRegInstruction("fmul", dst, left, right)

class FDiv(dst: D, left: D, right: D)
    : FloatThreeRegInstruction("fdiv", dst, left, right)

class Add(dst: IntRegister, left: IntRegister, right: IntRegister, shiftKind: ShiftKind? = null, imm: Int? = null)
    : IntRegShiftInstruction("add", dst, left, right, shiftKind, imm, ShiftKind.ROR)

class Sub(dst: IntRegister, left: IntRegister, right: IntRegister, shiftKind: ShiftKind? = null, imm: Int? = null)
    : IntRegShiftInstruction("sub", dst, left, right, shiftKind, imm, ShiftKind.ROR)

class SubImm(dst: IntRegister, left: IntRegister, imm12: Int)
    : IntTwoRegImmInstruction("sub", dst, left, imm12.toLong(), ImmKind.IMM12)

class Mul(dst: X, left: X, right: X)
    : IntRegInstruction("mul", dst, left, right)

class SMulh(dst: X, left: X, right: X)
    : IntRegInstruction("smulh", dst, left, right)

/** `dst = minuend - left * right` */
class MSub(dst: X, left: X, right: X, minuend: X)
    : IntThreeRegInstruction("msub", dst, left, right, minuend)

class SDiv(dst: X, left: X, right: X)
    : IntRegInstruction("sdiv", dst, left, right)

class Cmp(left: X, right: X)
    : IntTwoRegInstruction("cmp", left, right)

class FCmp(left: D, right: D)
    : FloatTwoRegInstruction("fcmp", left, right)

class CmpImm(left: X, imm12: Long)
    : IntOneRegImmInstruction("cmp", left, imm12, ImmKind.IMM12)

class CSet(dst: X, cond: ConditionFlag)
    : IntNoRegCFInstruction("cset", dst, cond)

class CSel(dst: X, left: X, right: X, cond: ConditionFlag)
    : IntRegCFInstruction("csel", dst, left, right, cond)

class CSNeg(dst: X, left: X, right: X, cond: ConditionFlag)
    : IntRegCFInstruction("csneg", dst, left, right, cond)

class Asr(dst: X, left: X, shift: X)
    : IntRegInstruction("asr", dst, left, shift)

class AsrImm(dst: X, left: X, imm: Int)
    : IntTwoRegImmInstruction("asr", dst, left, imm.toLong(), ImmKind.SHIFT)

class LslImm(dst: X, left: X, imm: Int)
    : IntTwoRegImmInstruction("lsl", dst, left, imm.toLong(), ImmKind.SHIFT)

class Neg(dst: X, src: X, shiftKind: ShiftKind? = null, imm: Int? = null)
    : IntOneRegShiftInstruction("neg", dst, src, shiftKind, imm, ShiftKind.ROR)

class Negs(dst: X, src: X, shiftKind: ShiftKind? = null, imm: Int? = null)
    : IntOneRegShiftInstruction("negs", dst, src, shiftKind, imm, ShiftKind.ROR)

class And(dst: X, left: X, right: X, shiftKind: ShiftKind? = null, imm: Int? = null)
    : IntRegShiftInstruction("and", dst, left, right, shiftKind, imm)

class AndImm(dst: X, left: X, imm: Long)
    : IntTwoRegImmInstruction("and", dst, left, imm, ImmKind.BIT_PATTERN)

class FMov(dst: D, from: Register)
    : FloatAnyRegInstruction("fmov", dst, from)

class FMovImm(dst: D, imm8: Double)
    : FloatOneRegImmInstruction("fmov", dst, imm8)

class Mov(val dst: IntRegister, val from: IntRegister)
    : IntTwoRegInstruction("mov", dst, from)

class MovBitPattern(dst: X, imm: Long)
    : IntOneRegImmInstruction("mov", dst, imm, ImmKind.BIT_PATTERN)

class MovZ(dst: X, value: Int, shift: Int)
    : IntOneRegImm16LslShiftInstruction("movz", dst ,value, shift)

class MovK(dst: X, value: Int, shift: Int)
    : IntOneRegImm16LslShiftInstruction("movk", dst ,value, shift)

class MovN(dst: X, value: Int, shift: Int)
    : IntOneRegImm16LslShiftInstruction("movn", dst ,value, shift)

class Adrp(reg: X, label: String)
    : IntRegLabelInstruction("adrp", reg, label)

class Str(reg: Register, address: IntRegister, offset: Any, mode: StpMode)
    : RegStoreLoadInstruction("str", reg, address, offset, mode)

class Ldr(dst: Register, address: IntRegister, offset: Any, mode: StpMode)
    : RegStoreLoadInstruction("ldr", dst, address, offset, mode)

class Stp(first: Register, second: Register, address: IntRegister, offset: Any, mode: StpMode)
    : TwoRegStoreLoadInstruction("stp", first, second, address, offset, mode)

class Ldp(first: Register, second: Register, address: IntRegister, offset: Any, mode: StpMode)
    : TwoRegStoreLoadInstruction("ldp", first, second, address, offset, mode)

class BL(label: String)
    : LabelInstruction("bl", label)

class B(label: String)
    : LabelInstruction("b", label)

class BCond(cond: ConditionFlag, label: String)
    : CondLabelInstruction("b", cond, label)

object Ret : Instruction("ret")

class Scvtf(dst: D, src: X)
    : FloatIntRegInstruction("scvtf", dst, src)

class Fcvtzs(dst: X, src: D)
    : IntFloatRegInstruction("fcvtzs", dst, src)