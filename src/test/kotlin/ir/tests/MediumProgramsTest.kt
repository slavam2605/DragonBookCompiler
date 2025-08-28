package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class MediumProgramsTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testMediumPrograms() = runTestsInFolder("/medium_programs")
}