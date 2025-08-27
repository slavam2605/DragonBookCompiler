package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class BreakContinueTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testBreakContinue() = runTestsInFolder("/break_continue")
}
