package compiler.backend.arm64

import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ControlFlowGraph
import java.io.File
import kotlin.io.path.createTempFile

object NativeMacAarch64 {
    fun compile(ffs: FrontendFunctions<ControlFlowGraph>, output: File? = null): File {
        val asm = buildAssembly(ffs)
        val lines = asm.map { op -> if (op is Label) op.toString() else "  $op" }

        val asmFile = output ?: createTempFile("dragon", ".s").toFile()
        asmFile.writeText(lines.joinToString("\n"))
        return asmFile
    }

    private fun buildAssembly(ffs: FrontendFunctions<ControlFlowGraph>): List<Instruction> {
        val ops = mutableListOf<Instruction>()

        // Generate assembly header
        ops.add(CustomText(".text"))
        ops.add(CustomText(".p2align 2"))
        ffs.values.forEach {
            ops.add(CustomText(".globl _${it.name}"))
        }

        // Generate assembly for each function
        ffs.values.forEach {
            Arm64AssemblyCompiler(it, ops).buildFunction()
        }

        return ops
    }
}