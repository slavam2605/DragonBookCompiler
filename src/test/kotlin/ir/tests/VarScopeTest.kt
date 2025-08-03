package ir.tests

import compiler.ir.IRVar
import ir.CompileToIRTestBase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class VarScopeTest : CompileToIRTestBase() {
    @TestFactory
    fun testVarScopes() = withFiles("/var_scopes") { file ->
        DynamicTest.dynamicTest("${file.name}") {
            val program = file.readText()
            val result = compileAndRun(program)[IRVar("xresult0")]
            assertEquals(-20, result)
        }
    }
}