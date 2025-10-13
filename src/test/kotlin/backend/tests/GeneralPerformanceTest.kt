package backend.tests

import backend.PerformanceTestBase
import org.junit.jupiter.api.TestFactory

class GeneralPerformanceTest : PerformanceTestBase() {
    @TestFactory
    fun ackermannTest() = runPerformanceTest(
        sourceFile = "performance_tests/ackermann/ackermann.txt",
        nativeRunner = "performance_tests/ackermann/runner.cpp",
        expectedPerformance = 0.78
    )

    @TestFactory
    fun countPrimesTest() = runPerformanceTest(
        sourceFile = "performance_tests/count_primes/count_primes.txt",
        nativeRunner = "performance_tests/count_primes/runner.cpp",
        expectedPerformance = 0.82
    )

    @TestFactory
    fun monteCarloTest() = runPerformanceTest(
        sourceFile = "performance_tests/monte_carlo_pi/monte_carlo_pi.txt",
        nativeRunner = "performance_tests/monte_carlo_pi/runner.cpp",
        expectedPerformance = 0.59
    )

    @TestFactory
    fun newtonSqrtTest() = runPerformanceTest(
        sourceFile = "performance_tests/newton_sqrt/newton_sqrt.txt",
        nativeRunner = "performance_tests/newton_sqrt/runner.cpp",
        expectedPerformance = 0.77
    )

    @TestFactory
    fun divisionTest() = runPerformanceTest(
        sourceFile = "performance_tests/division/division.txt",
        nativeRunner = "performance_tests/division/runner.cpp",
        expectedPerformance = 0.39
    )
}
