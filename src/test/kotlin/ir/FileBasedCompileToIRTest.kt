package ir

import compiler.frontend.CompilationFailed
import compiler.ir.printToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.io.File
import kotlin.test.assertTrue

abstract class FileBasedCompileToIRTest : CompileToIRTestBase() {
    private data class ExpectedValue(val varName: String, val expectedValue: Long?)
    private data class ExpectedError(val line: Int, val col: Int, val message: String)

    /**
     * Reads the file and runs an automated test.
     * Expected values should be written in the file itself in comments, e.g. `// expected: result == 10`
     */
    protected fun runTestFromFile(mode: TestMode, file: File): DynamicTest {
        return DynamicTest.dynamicTest(file.name) {
            val testProgram = file.readText()
            val expectedRegex = "// *expected: *([a-zA-Z0-9_]+) *== *([a-zA-Z0-9_]+)".toRegex()
            val expectedValues = testProgram.lines().mapNotNull { line ->
                expectedRegex.find(line)?.let {
                    val (varName, expectedValue) = it.destructured
                    val value = if (expectedValue == "unknown") null else expectedValue.toLong()
                    ExpectedValue(varName, value)
                }
            }
            val errorRegex = "// *error: *([0-9]+):([0-9]+)* *\"(.*)\"".toRegex()
            val expectedErrors = testProgram.lines().mapNotNull { line ->
                errorRegex.find(line)?.let {
                    val (line, col, message) = it.destructured
                    ExpectedError(line.toInt(), col.toInt(), message)
                }
            }
            val directiveRegex = "// *#([a-zA-Z_0-9]+)".toRegex()
            val directives = testProgram.lines().mapNotNull { line ->
                directiveRegex.find(line)?.let { it.groupValues[1] }
            }
            val allowUndef = "allow_undef" in directives

            val visitedErrors = mutableSetOf<ExpectedError>()
            try {
                val result = compileAndRun(mode, testProgram, simulateUndef = allowUndef)
                if (PRINT_DEBUG_INFO) {
                    println("\nResults:")
                    result.forEach { (varName, value) ->
                        println("\t${varName.printToString()} == $value")
                    }
                }

                expectedValues.forEach { (varName, expectedValue) ->
                    val actualValue = result.getVariable(varName)
                    assertEquals(expectedValue, actualValue) {
                        "Expected $varName == $expectedValue, but got $actualValue"
                    }
                }
            } catch (e: CompilationFailed) {
                e.exceptions.forEach { exception ->
                    val ctx = exception.location ?: throw RuntimeException("Exceptions without location are not supported", exception)
                    val expectedError = expectedErrors.find {
                        it.line == ctx.line && it.col == ctx.start && it.message == exception.message
                    }
                    assertTrue(expectedError != null, "Unexpected error: ${ctx.line}:${ctx.start} \"${exception.message}\"")
                    visitedErrors.add(expectedError)
                    if (PRINT_DEBUG_INFO) {
                        println("Expected exception: ${exception.message} at ${ctx.line}:${ctx.start}")
                    }
                }
            }
            expectedErrors.forEach { error ->
                assertTrue(error in visitedErrors, "Expected error was not thrown: ${error.line}:${error.col} \"${error.message}\"")
            }
        }
    }

    protected fun runTestsInFolder(folder: String): List<DynamicNode> {
        return withFiles(folder) { mode, file -> runTestFromFile(mode, file) }
    }
}