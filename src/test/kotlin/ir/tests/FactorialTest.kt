package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class FactorialTest : CompileToIRTestBase() {
    private fun factorial(n: Long) = (1..n).fold(1L, Long::times)

    @TestFactory
    fun testFactorial() = withParametersAndFiles(0L .. 20L, "/factorial") { mode, n, file ->
        DynamicTest.dynamicTest("${file.name} [n = $n]") {
            val program = readWithPattern(file, "n" to n)
            val result = compileAndGet(mode, program, "result")
            println("factorial($n) = $result")
            assertEquals(factorial(n), result)
        }
    }
}