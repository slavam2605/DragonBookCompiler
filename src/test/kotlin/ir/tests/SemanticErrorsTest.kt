package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.TestFactory

class SemanticErrorsTest : CompileToIRTestBase() {
    @TestFactory
    fun testSemanticErrors() = withFiles("/semantic_errors") { mode, file ->
        runTestFromFile(mode, file)
    }
}
