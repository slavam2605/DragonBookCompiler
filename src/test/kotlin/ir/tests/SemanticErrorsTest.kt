package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class SemanticErrorsTest : FileBasedCompileToIRTest() {
    override val excludeModes = setOf(TestMode.NATIVE_ARM64)

    @TestFactory
    fun testSemanticErrors() = runTestsInFolder("/semantic_errors")
}
