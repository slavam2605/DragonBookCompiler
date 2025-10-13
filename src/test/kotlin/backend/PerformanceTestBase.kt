package backend

import ir.FileBasedCompileToIRTest
import ir.NativeTestOptions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

abstract class PerformanceTestBase : FileBasedCompileToIRTest() {
    private var expectedPerformance: Double = 0.0
    private lateinit var nativeRunner: String

    override val excludeModes = TestMode.entries.toSet() - setOf(TestMode.OPTIMIZED_NON_SSA, TestMode.NATIVE_ARM64)

    override val nativeTestOptions
        get() = NativeTestOptions(
            customNativeRunner = nativeRunner,
            linkFiles = listOf(BENCHMARK_HELPER),
            useCppCompiler = true,
            compilerFlags = listOf("-O1"),
            testRunTimeout = 10.seconds
        )

    /**
     * Runs a performance test that compares performance of `performance_target`
     * in [sourceFile] with `performance_target_gold` in [nativeRunner].
     *
     * [expectedPerformance] is a ratio between gold and actual time:
     *  - `< 1.0` means that actual performance is slower than gold.
     *  - `1.0` means that actual performance is as good as gold.
     *  - `> 1.0` means that actual performance is faster than gold.
     */
    protected fun runPerformanceTest(
        sourceFile: String,
        nativeRunner: String,
        expectedPerformance: Double
    ): List<DynamicNode> {
        if (TestResources.getURL(BENCHMARK_HELPER) == null) {
            return listOf(DynamicTest.dynamicTest("Benchmark helper is missing") {
                println("To run performance tests, you need to build benchmark helper first. " +
                        "Run `./benchmark_helper/setup_benchmark.sh`.")
                Assumptions.assumeTrue(false)
            })
        }

        this.nativeRunner = nativeRunner
        this.expectedPerformance = expectedPerformance
        return runTestsInFolder(sourceFile)
    }

    private fun parseTimeNs(benchmarkName: String, output: String): Pair<Double, Double> {
        val regex = Regex("$benchmarkName\\s+([0-9]+\\.?[0-9]*) ns\\s+([0-9]+\\.?[0-9]*) ns")
        val match = regex.find(output) ?: error("Failed parse benchmark result: $benchmarkName")
        return match.groupValues[1].toDouble() to match.groupValues[2].toDouble()
    }

    override fun handleNativeOutput(output: String) {
        val (_, actualCpuTime) = parseTimeNs("BM_Target", output)
        val (_, goldCpuTime) = parseTimeNs("BM_Gold", output)
        val actualPerformance = goldCpuTime / actualCpuTime * 100
        val expectedPerformance = expectedPerformance * 100
        if (PRINT_DEBUG_INFO) {
            println("Benchmark results:")
            println("\tActual time: $actualCpuTime ns")
            println("\tGold time: $goldCpuTime ns")
            println("\tGold / Actual = ${actualPerformance.roundToInt()}%")
            println("\tExpected ratio: ${expectedPerformance.roundToInt()}%")
            println()
        }

        val performanceDiff = actualPerformance - expectedPerformance
        assertTrue(
            actual = performanceDiff.absoluteValue < 5,
            message = "Expected performance was ${expectedPerformance.roundToInt()}%, " +
                    "but actual was ${actualPerformance.roundToInt()}%, " +
                    "difference: ${performanceDiff.roundToInt()}%"
        )
    }

    companion object {
        private const val BENCHMARK_HELPER = "native/benchmark_helper.o"
    }
}