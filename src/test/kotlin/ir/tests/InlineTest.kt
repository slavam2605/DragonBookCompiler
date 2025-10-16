package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class InlineTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testInlining() = runTestsInFolder("/inline_tests")
}