package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class LogicOpsTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testLogicAndOr() = runTestsInFolder("/logic")
}
