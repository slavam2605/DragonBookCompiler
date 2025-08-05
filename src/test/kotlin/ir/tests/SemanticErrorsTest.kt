package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class SemanticErrorsTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testSemanticErrors() = runTestsInFolder("/semantic_errors")
}
