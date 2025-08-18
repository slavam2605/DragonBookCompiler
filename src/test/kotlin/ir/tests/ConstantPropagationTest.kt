package ir.tests

import ir.FileBasedStaticEvaluationTest
import org.junit.jupiter.api.TestFactory

class ConstantPropagationTest : FileBasedStaticEvaluationTest() {
    @TestFactory
    fun testConstantPropagation() = runTestsInFolder("/constant_propagation")
}