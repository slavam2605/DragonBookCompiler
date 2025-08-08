package ir

import compiler.frontend.CompilationFailed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.io.File
import kotlin.test.assertTrue

abstract class FileBasedCompileToIRTest : CompileToIRTestBase() {
    private data class ExpectedValue(val varName: String, val expectedValue: Long)
    private data class ExpectedError(val line: Int, val col: Int, val message: String)

    /**
     * Reads the file and runs an automated test.
     * Expected values should be written in the file itself in comments, e.g. `// expected: result == 10`
     */
    protected fun runTestFromFile(mode: TestMode, file: File): DynamicTest {
        return DynamicTest.dynamicTest(file.name) {
            val testProgram = file.readText()
            val expectedRegex = "// *expected: *([a-zA-Z0-9_]+) *== *([0-9]+)".toRegex()
            val expectedValues = testProgram.lines().mapNotNull { line ->
                expectedRegex.find(line)?.let {
                    val (varName, expectedValue) = it.destructured
                    ExpectedValue(varName, expectedValue.toLong())
                }
            }
            val errorRegex = "// *error: *([0-9]+):([0-9]+)* *\"(.*)\"".toRegex()
            val expectedErrors = testProgram.lines().mapNotNull { line ->
                errorRegex.find(line)?.let {
                    val (line, col, message) = it.destructured
                    ExpectedError(line.toInt(), col.toInt(), message)
                }
            }

            val visitedErrors = mutableSetOf<ExpectedError>()
            try {
                val result = compileAndRun(mode, testProgram)
                expectedValues.forEach { (varName, expectedValue) ->
                    val actualValue = result.getVariable(varName)
                    assertEquals(expectedValue, actualValue) {
                        "Expected $varName == $expectedValue, but got $actualValue"
                    }
                }
            } catch (e: CompilationFailed) {
                e.exceptions.forEach { exception ->
                    val ctx = exception.location ?: error("Exceptions without location are not supported")
                    val expectedError = expectedErrors.find {
                        it.line == ctx.line && it.col == ctx.start && it.message == exception.message
                    }
                    assertTrue(expectedError != null, "Unexpected error: ${ctx.line}:${ctx.start} \"${exception.message}\"")
                    visitedErrors.add(expectedError)
                    println("Expected exception: ${exception.message} at ${ctx.line}:${ctx.start}")
                }
            }
            expectedErrors.forEach { error ->
                assertTrue(error in visitedErrors, "Expected error was not thrown: ${error.line}:${error.col} \"${error.message}\"")
            }

            check(expectedValues.isNotEmpty() || expectedErrors.isNotEmpty()) {
                "No assertions found in file $file"
            }
        }
    }

    protected fun runTestsInFolder(folder: String): List<DynamicNode> {
        return withFiles(folder) { mode, file -> runTestFromFile(mode, file) }
    }
}