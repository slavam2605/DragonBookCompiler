package backend

import compiler.backend.arm64.Arm64CompilationFlow
import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ControlFlowGraph
import ir.CompileToIRTestBase.Companion.PRINT_DEBUG_INFO
import utils.runProcess
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object NativeArm64TestCompilationFlow {
    private val compiledHelpers = mutableMapOf<String, File>()

    fun compileAndRun(ffs: FrontendFunctions<ControlFlowGraph>, customNativeRunner: String?): String {
        val asmFile = Arm64CompilationFlow.compileToAsm(ffs)
        if (PRINT_DEBUG_INFO) {
            println(asmFile.readText())
        }

        val helperFile = compileHelper(customNativeRunner)
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

    private fun compileHelper(customNativeRunner: String?): File {
        val helperFileName = customNativeRunner ?: "native/test.c"
        if (helperFileName !in compiledHelpers) {
            val inputPath = TestResources.getFile(helperFileName).absolutePath
            val outputFile = createTempFile("test", ".o").toFile()
            runProcess("clang", "-c", "-arch", "arm64", inputPath, "-o", outputFile.absolutePath) { code, output ->
                assertEquals(code, 0, "Failed to compile test.c:\n$output")
            }
            compiledHelpers[helperFileName] = outputFile
        }
        return compiledHelpers[helperFileName]!!
    }
}