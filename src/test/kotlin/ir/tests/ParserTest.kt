package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class ParserTest : FileBasedCompileToIRTest() {
    override val excludeModes = TestMode.entries.toSet() - TestMode.IR

    @TestFactory
    fun testParser() = runTestsInFolder("/parser_tests")
}