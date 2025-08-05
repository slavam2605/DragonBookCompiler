package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class VarScopeTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testVarScopes() = runTestsInFolder("/var_scopes")
}