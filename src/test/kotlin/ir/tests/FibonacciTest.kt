package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class FibonacciTest : CompileToIRTestBase() {
    private fun fibonacci(n: Long) = generateSequence(0L to 1L) { (a, b) -> b to a + b }
        .map { it.first }.elementAt(n.toInt())

    @TestFactory
    fun testFibonacci() = withParametersAndFiles(0L .. 94L, "/fibonacci") { n, file ->
        DynamicTest.dynamicTest("${file.name}($n)") {
            val program = readWithPattern(file, "n" to n)
            val result = compileAndGet(program, "result")
            assertEquals(fibonacci(n), result)
        }
    }
}