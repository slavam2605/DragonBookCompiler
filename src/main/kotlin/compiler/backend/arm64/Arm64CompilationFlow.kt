package compiler.backend.arm64

import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ControlFlowGraph
import java.io.File
import kotlin.io.path.createTempFile

object Arm64CompilationFlow {
    fun compileToAsm(functions: FrontendFunctions<ControlFlowGraph>, outputFile: File? = null): File {
        val outputFile = outputFile ?: createTempFile("out", ".s").toFile()
        NativeMacAarch64.compile(functions, outputFile)
        return outputFile
    }
}