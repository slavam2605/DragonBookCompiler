package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class FunctionsTests : FileBasedCompileToIRTest() {
    override val excludeModes = setOf(TestMode.IR)

    @TestFactory
    fun testFunctions() = runTestsInFolder("/functions_tests")
}