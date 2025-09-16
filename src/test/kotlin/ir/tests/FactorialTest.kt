package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class FactorialTest : CompileToIRTestBase() {
    override val customNativeRunner = "factorial/native/runner.c"

    private fun factorial(n: Long) = (1..n).fold(1L, Long::times)

    @TestFactory
    fun testFactorial() = withParametersAndFiles(0L .. 20L, "/factorial") { mode, n, file ->
        if (mode == TestMode.NATIVE_ARM64 && n > 0) return@withParametersAndFiles null
        val testName = if (mode == TestMode.NATIVE_ARM64) "[n in 0..20]" else "[n = $n]"
        DynamicTest.dynamicTest(testName) {
            val program = readWithPattern(file, "n" to n)
            val result = compileAndGetResult(mode, program)
            if (PRINT_DEBUG_INFO) {
                println("factorial($n) = $result")
            }
            if (mode != TestMode.NATIVE_ARM64) {
                assertEquals(factorial(n), result)
            }
        }
    }
}