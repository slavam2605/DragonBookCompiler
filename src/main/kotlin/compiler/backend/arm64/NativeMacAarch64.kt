package compiler.backend.arm64

import compiler.backend.PrepareForNativeCompilation
import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ControlFlowGraph
import java.io.File

object NativeMacAarch64 {
    fun compile(ffs: FrontendFunctions<ControlFlowGraph>, output: File) {
        val asm = buildAssembly(ffs)
        val lines = asm.map { op -> if (op is Label) op.toString() else "  $op" }

        output.writeText(lines.joinToString("\n"))
    }

    private fun buildAssembly(ffs: FrontendFunctions<ControlFlowGraph>): List<Instruction> {
        val ops = mutableListOf<Instruction>()
        val constPool = Arm64ConstantPool()

        // Generate assembly header
        ops.add(CustomText(".text"))
        ops.add(CustomText(".p2align 2"))
        ffs.values.forEach {
            ops.add(CustomText(".globl _${it.name}"))
        }

        // Generate assembly for each function
        ffs.values.forEach {
            require(PrepareForNativeCompilation.isPrepared(it.value))
            Arm64AssemblyCompiler(it, constPool, ops).buildFunction()
        }

        // Write constant pool to the end of the file
        constPool.writeSection(ops)

        return ops
    }
}