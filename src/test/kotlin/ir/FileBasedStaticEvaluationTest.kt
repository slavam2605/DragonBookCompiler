package ir

abstract class FileBasedStaticEvaluationTest : FileBasedCompileToIRTest() {
    override val excludeModes: Set<TestMode>
        get() = setOf(TestMode.IR, TestMode.CFG, TestMode.SSA)

    override val ignoreInterpretedValues: Boolean
        get() = true
}