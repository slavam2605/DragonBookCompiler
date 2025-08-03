package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class FactorialTest : CompileToIRTestBase() {
    private fun factorial(n: Long) = (1..n).fold(1L, Long::times)

    @TestFactory
    fun testFactorial() = withParametersAndFiles(0L .. 20L, "/factorial") { n, file ->
        DynamicTest.dynamicTest("${file.name}($n)") {
            val program = readWithPattern(file, "n" to n)
            val result = compileAndGet(program, "result")
            println("factorial($n) = $result")
            assertEquals(factorial(n), result)
        }
    }
}