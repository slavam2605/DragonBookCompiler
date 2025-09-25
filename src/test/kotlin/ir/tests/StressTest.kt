package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class StressTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun stressTest() = runTestsInFolder("/stress_test")
}