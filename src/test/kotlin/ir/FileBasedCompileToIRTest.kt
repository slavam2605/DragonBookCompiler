package ir

import MainLexer
import compiler.backend.arm64.registerAllocation.MemoryAllocator
import compiler.frontend.CompilationFailed
import compiler.ir.printToString
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import statistics.StatsHolder
import java.io.File
import kotlin.test.assertTrue

abstract class FileBasedCompileToIRTest : CompileToIRTestBase() {
    private data class ExpectedError(val line: Int, val col: Int, val message: String)
    private data class ExpectedMemoryAllocation(val functionName: String, val used: Int, val spilled: Int)

    /**
     * Reads the file and runs an automated test.
     * Expected values should be written in the file itself in comments, e.g. `// expected: result == 10`
     */
    protected fun runTestFromFile(mode: TestMode, file: File): DynamicTest {
        return DynamicTest.dynamicTest(file.name) {
            val testProgram = file.readText()
            val expectedErrors = parseExpectedErrors(testProgram)
            val expectedMemoryAllocations = parseExpectedMemoryAllocation(testProgram)

            val visitedErrors = mutableSetOf<ExpectedError>()
            try {
                val result = compileAndRun(mode, testProgram)
                assertMemoryAllocation(mode, expectedMemoryAllocations)
                if (PRINT_DEBUG_INFO) {
                    println("\nResults:")
                    result.forEach { (varName, value) ->
                        println("\t${varName.printToString()} == $value")
                    }
                }
            } catch (e: CompilationFailed) {
                // Print compilation errors as in normal compilation
                val lexer = MainLexer(CharStreams.fromString(testProgram))
                val tokens = CommonTokenStream(lexer)
                e.printErrors(tokens)

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

    private fun assertMemoryAllocation(mode: TestMode, expected: List<ExpectedMemoryAllocation>) {
        if (mode != TestMode.NATIVE_ARM64) {
            return
        }

        expected.forEach { (functionName, used, spilled) ->
            val actualUsed = StatsHolder.get<MemoryAllocator.StatUsedRegisters>(functionName).value
            val actualSpilled = StatsHolder.get<MemoryAllocator.StatSpilledRegisters>(functionName).value
            assertEquals(used, actualUsed)
            assertEquals(spilled, actualSpilled)
        }
    }

    private fun parseExpectedErrors(testProgram: String): List<ExpectedError> {
        val errorRegex = "// *error: *([0-9]+):([0-9]+)* *\"(.*)\"".toRegex()
        return testProgram.lines().mapNotNull { line ->
            errorRegex.find(line)?.let {
                val (line, col, message) = it.destructured
                ExpectedError(line.toInt(), col.toInt(), message)
            }
        }
    }

    private fun parseExpectedMemoryAllocation(testProgram: String): List<ExpectedMemoryAllocation> {
        val errorRegex = "// *memory_allocation +([a-zA-Z0-9_]+) *: *used *([0-9]+) *, *spilled *([0-9]+)".toRegex()
        return testProgram.lines().mapNotNull { line ->
            errorRegex.find(line)?.let {
                val (functionName, used, spilled) = it.destructured
                ExpectedMemoryAllocation(functionName, used.toInt(), spilled.toInt())
            }
        }
    }

    protected fun runTestsInFolder(folder: String): List<DynamicNode> {
        return withFiles(folder) { mode, file -> runTestFromFile(mode, file) }
    }
}