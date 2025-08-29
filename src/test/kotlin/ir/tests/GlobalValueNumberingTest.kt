package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class GlobalValueNumberingTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testGlobalValueNumbering() = runTestsInFolder("/value_numbering")
}