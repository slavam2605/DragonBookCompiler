package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

class FloatNumbersTest : FileBasedCompileToIRTest() {
    @TestFactory
    fun testFloatNumbers() = runTestsInFolder("/float_numbers")
}