package ir

abstract class FileBasedStaticEvaluationTest : FileBasedCompileToIRTest() {
    override val excludeModes: Set<TestMode>
        get() = setOf(TestMode.IR, TestMode.CFG, TestMode.SSA, TestMode.OPTIMIZED_NON_SSA, TestMode.NATIVE_ARM64)

    override val ignoreInterpretedValues: Boolean
        get() = true
}