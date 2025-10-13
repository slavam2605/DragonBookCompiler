package compiler.backend.arm64.ops.utils

import compiler.backend.arm64.instructions.FMov
import compiler.backend.arm64.IntRegister.SP
import compiler.backend.arm64.IntRegister.X
import compiler.backend.arm64.instructions.Ldr
import compiler.backend.arm64.MemoryLocation
import compiler.backend.arm64.instructions.Mov
import compiler.backend.arm64.NativeCompilerContext
import compiler.backend.arm64.Register.D
import compiler.backend.arm64.StackLocation
import compiler.backend.arm64.instructions.StpMode
import compiler.backend.arm64.instructions.Str
import compiler.ir.IRFloat
import compiler.ir.IRInt
import compiler.ir.IRValue
import compiler.ir.IRVar

object CopyUtils {
    /**
     * Helper function for emitting copy of a memory location [src] to another memory location [dst].
     */
    fun emitCopy(context: NativeCompilerContext, dst: MemoryLocation, src: MemoryLocation) {
        if (src == dst) return

        val spShift = context.spShift
        val ops = context.ops
        when (dst) {
            is X -> when (src) {
                is X -> ops.add(Mov(dst, src))
                is StackLocation -> ops.add(Ldr(dst, SP, src.spOffset(spShift), StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory locations: $dst <- $src")
            }
            is D -> when (src) {
                is D -> ops.add(FMov(dst, src))
                is StackLocation -> ops.add(Ldr(dst, SP, src.spOffset(spShift), StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory locations: $dst <- $src")
            }
            is StackLocation -> when (src) {
                is X -> ops.add(Str(src, SP, dst.spOffset(spShift), StpMode.SIGNED_OFFSET))
                is D -> ops.add(Str(src, SP, dst.spOffset(spShift), StpMode.SIGNED_OFFSET))
                else -> error("Unsupported memory location: $dst <- $src")
            }
            else -> error("Unsupported memory location: $dst <- $src")
        }
    }

    /**
     * Helper function for emitting copy of any [value] to a memory location [dst].
     */
    fun emitCopy(context: NativeCompilerContext, dst: MemoryLocation, value: IRValue) {
        when (value) {
            is IRVar -> emitCopy(context, dst, context.allocator.loc(value))
            is IRInt -> {
                when (dst) {
                    is X -> NumberUtils.emitAssignConstantInt64(context, dst, value.value)
                    is StackLocation -> {
                        context.allocator.tempIntReg { reg ->
                            NumberUtils.emitAssignConstantInt64(context, reg, value.value)
                            emitCopy(context, dst, reg)
                        }
                    }
                    else -> error("Unsupported memory location: $dst <- $value")
                }
            }
            is IRFloat -> {
                when (dst) {
                    is D -> NumberUtils.emitAssignConstantFloat64(context, dst, value.value)
                    else -> error("Unsupported memory location: $dst <- $value")
                }
            }
        }
    }
}