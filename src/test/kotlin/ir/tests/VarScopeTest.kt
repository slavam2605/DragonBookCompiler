package ir.tests

import ir.CompileToIRTestBase
import org.junit.jupiter.api.TestFactory

class VarScopeTest : CompileToIRTestBase() {
    @TestFactory
    fun testVarScopes() = withFiles("/var_scopes") { file ->
        runTestFromFile(file)
    }
}