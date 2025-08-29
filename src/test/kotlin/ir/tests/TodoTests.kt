package ir.tests

import ir.FileBasedCompileToIRTest
import org.junit.jupiter.api.TestFactory

/**
 * This test contains tests for not implemented yet features or optimizations.
 * Such tests should assert that this feature is not implemented if possible,
 * for example, by using `assertStaticUnknown` instead of `assertStaticEquals`.
 */
class TodoTests : FileBasedCompileToIRTest() {
    @TestFactory
    fun verifyStillNotImplemented() = runTestsInFolder("/todo_tests")
}