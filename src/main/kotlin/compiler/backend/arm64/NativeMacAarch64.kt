package compiler.backend.arm64

import compiler.ir.cfg.ControlFlowGraph
import java.io.File
import kotlin.io.path.createTempFile

object NativeMacAarch64 {
    data class Options(
        val output: File? = null,
        val entrySymbol: String = "_main"
    )

    fun compile(cfg: ControlFlowGraph, options: Options): File {
        val asm = Arm64AssemblyCompiler(cfg).buildAssembly(options.entrySymbol)
        val lines = asm.map { op -> if (op is Label) op.toString() else "  $op" }

        val asmFile = options.output ?: createTempFile("dragon", ".s").toFile()
        asmFile.writeText(lines.joinToString("\n"))
        return asmFile
    }
}