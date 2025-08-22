package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class CollatzTest : CompileToIRTestBase() {
    private fun collatz(n: Long) = generateSequence(n) {
        when {
            it == 1L -> null
            it % 2 == 0L -> it / 2
            else -> it * 3 + 1
        }
    }.count() - 1

    @TestFactory
    fun testCollatz(): List<DynamicNode> {
        val parameters = generateRandomParameters(1000, 1L .. Int.MAX_VALUE)
        return withParametersAndFiles(parameters, "/collatz") { mode, n, file ->
            DynamicTest.dynamicTest("${file.name} [n = $n]") {
                val program = readWithPattern(file, "n" to n)
                val result = compileAndGet(mode, program, "result")
                if (PRINT_DEBUG_INFO) {
                    println("collatz steps for $n = $result")
                }
                assertEquals(collatz(n).toLong(), result)
            }
        }
    }
}