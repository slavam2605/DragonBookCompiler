package backend

import compiler.backend.arm64.Arm64CompilationFlow
import compiler.frontend.FrontendFunctions
import compiler.ir.cfg.ControlFlowGraph
import ir.CompileToIRTestBase.Companion.PRINT_DEBUG_INFO
import ir.NativeTestOptions
import utils.runProcess
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object NativeArm64TestCompilationFlow {
    private val compiledHelpers = mutableMapOf<String, File>()

    fun compileAndRun(ffs: FrontendFunctions<ControlFlowGraph>, testOptions: NativeTestOptions): String {
        val asmFile = Arm64CompilationFlow.compileToAsm(ffs)
        if (PRINT_DEBUG_INFO) {
            println(asmFile.readText())
        }

        val helperFile = compileHelper(testOptions)
        val outputFile = createTempFile("test").toFile()
        val linkFiles = testOptions.linkFiles
            .map { TestResources.getFile(it).absolutePath }
            .toTypedArray()

        // Compile the test program and link with helper and additional link files
        runProcess(
            clang(testOptions.useCppCompiler),
            "-arch", "arm64",
            *testOptions.compilerFlags.toTypedArray(),
            helperFile.absolutePath,
            asmFile.absolutePath,
            *linkFiles,
            "-o", outputFile.absolutePath
        ) { code, output ->
            assertEquals(code, 0, "Failed to compile:\n$output")
        }
        assertTrue(outputFile.exists() && outputFile.canExecute(),
            "Executable file was not produced: $outputFile")

        // Run a compiled test
        val output = runProcess(outputFile.absolutePath, timeoutInSeconds = testOptions.testRunTimeout.inWholeSeconds) { code, output ->
            assertEquals(code, 0, "Executable failed to execute:\n$output")
        }

        return output.trim()
    }

    private fun compileHelper(testOptions: NativeTestOptions): File {
        val helperFileName = testOptions.customNativeRunner ?: "native/test.c"
        if (helperFileName !in compiledHelpers) {
            val inputPath = TestResources.getFile(helperFileName).absolutePath
            val outputFile = createTempFile("test", ".o").toFile()
            runProcess(
                clang(testOptions.useCppCompiler),
                "-c", "-arch", "arm64",
                *testOptions.compilerFlags.toTypedArray(),
                inputPath, "-o", outputFile.absolutePath
            ) { code, output ->
                assertEquals(code, 0, "Failed to compile test.c:\n$output")
            }
            compiledHelpers[helperFileName] = outputFile
        }
        return compiledHelpers[helperFileName]!!
    }

    private fun clang(useCppCompiler: Boolean) = if (useCppCompiler) "clang++" else "clang"
}