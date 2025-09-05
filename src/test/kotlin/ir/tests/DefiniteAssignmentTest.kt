package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class DefiniteAssignmentTest : FileBasedCompileToIRTest() {
    override val excludeModes = setOf(TestMode.IR, TestMode.NATIVE_ARM64)

    @TestFactory
    fun testDefiniteAssignment() = runTestsInFolder("/definite_assignment")
}