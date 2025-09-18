package ir.tests

import compiler.frontend.FrontendConstantValue
import ir.CompileToIRTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class CollatzTest : CompileToIRTestBase() {
    override val customNativeRunner = "collatz/native/runner.c"

    private fun collatz(n: Long) = generateSequence(n) {
        when {
            it == 1L -> null
            it % 2 == 0L -> it / 2
            else -> it * 3 + 1
        }
    }.count() - 1

    @TestFactory
    fun testCollatz(): List<DynamicNode> {
        val parameters = generateRandomParameters(100, 1L ..Int.MAX_VALUE / 20).map { it * it }
        return withParametersAndFiles(parameters, "/collatz") { mode, n, file ->
            if (mode == TestMode.NATIVE_ARM64 && n != parameters[0]) return@withParametersAndFiles null
            val testName = if (mode == TestMode.NATIVE_ARM64) "[all n]" else "[n = $n]"
            DynamicTest.dynamicTest(testName) {
                val program = readWithPattern(file, "n" to n)
                val result = compileAndGetResult(mode, program)
                if (PRINT_DEBUG_INFO) {
                    println("collatz steps for $n = $result")
                }
                if (mode != TestMode.NATIVE_ARM64) {
                    assertEquals(collatz(n).toLong(), (result as? FrontendConstantValue.IntValue)?.value)
                }
            }
        }
    }
}