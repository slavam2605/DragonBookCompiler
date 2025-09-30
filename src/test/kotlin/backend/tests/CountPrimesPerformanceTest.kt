package backend.tests

import backend.PerformanceTestBase
import org.junit.jupiter.api.TestFactory

class CountPrimesPerformanceTest : PerformanceTestBase("performance_tests/count_primes/runner.cpp") {
    @TestFactory
    fun countPrimesTest() = runPerformanceTest("/performance_tests/count_primes")
}