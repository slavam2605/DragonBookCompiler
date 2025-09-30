package backend

import ir.FileBasedCompileToIRTest
import ir.NativeTestOptions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import kotlin.time.Duration.Companion.seconds

abstract class PerformanceTestBase(nativeRunner: String) : FileBasedCompileToIRTest() {
    override val excludeModes = TestMode.entries.toSet() - setOf(TestMode.OPTIMIZED_SSA, TestMode.NATIVE_ARM64)

    override val nativeTestOptions = NativeTestOptions(
        customNativeRunner = nativeRunner,
        linkFiles = listOf(BENCHMARK_HELPER),
        useCppCompiler = true,
        compilerFlags = listOf("-O1"),
        testRunTimeout = 10.seconds
    )

    protected fun runPerformanceTest(folder: String): List<DynamicNode> {
        if (TestResources.getURL(BENCHMARK_HELPER) == null) {
            return listOf(DynamicTest.dynamicTest("Benchmark helper is missing") {
                println("To run performance tests, you need to build benchmark helper first. " +
                        "Run `./benchmark_helper/setup_benchmark.sh`.")
                Assumptions.assumeTrue(false)
            })
        }
        return runTestsInFolder(folder)
    }

    companion object {
        private const val BENCHMARK_HELPER = "native/benchmark_helper.o"
    }
}