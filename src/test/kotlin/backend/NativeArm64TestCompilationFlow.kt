package backend

import compiler.backend.arm64.NativeMacAarch64
import compiler.ir.cfg.ControlFlowGraph
import ir.CompileToIRTestBase.Companion.PRINT_DEBUG_INFO
import utils.runProcess
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object NativeArm64TestCompilationFlow {
    private var compiledHelper: File? = null

    fun compileAndRun(cfg: ControlFlowGraph): String {
        val asmFile = NativeMacAarch64.compile(cfg, NativeMacAarch64.Options(entrySymbol = "_foo"))
        if (PRINT_DEBUG_INFO) {
            println(asmFile.readText())
        }

        val helperFile = compileHelper()
        val outputFile = createTempFile("test").toFile()

        // Compile test program and Link with helper
        runProcess(
            "clang", "-arch", "arm64",
            helperFile.absolutePath,
            asmFile.absolutePath,
            "-o", outputFile.absolutePath
        ) { code, output ->
            assertEquals(code, 0, "Failed to compile:\n$output")
        }
        assertTrue(outputFile.exists() && outputFile.canExecute(),
            "Executable file was not produced: $outputFile")

        // Run a compiled test
        val output = runProcess(outputFile.absolutePath) { code, output ->
            assertEquals(code, 0, "Executable failed to execute:\n$output")
        }

        return output.trim()
    }

    private fun compileHelper(): File {
        val inputPath = TestResources.getFile("native/test.c").absolutePath
        if (compiledHelper == null) {
            val outputFile = createTempFile("test", ".o").toFile()
            runProcess("clang", "-c", "-arch", "arm64", inputPath, "-o", outputFile.absolutePath) { code, output ->
                assertEquals(code, 0, "Failed to compile test.c:\n$output")
            }
            compiledHelper = outputFile
        }
        return compiledHelper!!
    }
}