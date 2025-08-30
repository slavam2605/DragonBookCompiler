package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class FromSSATest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testFromSSA() = runTestsInFolder("/from_ssa")
}