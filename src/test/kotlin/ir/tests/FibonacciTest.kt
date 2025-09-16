package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class FibonacciTest : CompileToIRTestBase() {
    override val customNativeRunner = "fibonacci/native/runner.c"

    private fun fibonacci(n: Long) = generateSequence(0L to 1L) { (a, b) -> b to a + b }
        .map { it.first }.elementAt(n.toInt())

    @TestFactory
    fun testFibonacci() = withParametersAndFiles(0L .. 94L, "/fibonacci") { mode, n, file ->
        if (mode == TestMode.NATIVE_ARM64 && n > 0) return@withParametersAndFiles null
        val testName = if (mode == TestMode.NATIVE_ARM64) "[n in 0..94]" else "[n = $n]"
        DynamicTest.dynamicTest(testName) {
            val program = readWithPattern(file, "n" to n)
            val result = compileAndGetResult(mode, program)
            if (mode != TestMode.NATIVE_ARM64) {
                assertEquals(fibonacci(n), result)
            }
        }
    }
}