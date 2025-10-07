#include <benchmark/benchmark.h>
#include <iostream>

extern "C" long long performance_target(long long);
extern "C" long long performance_gold(long long);
extern "C" void test_main();

extern "C" void assertEquals(long long a, long long b) {
    if (a == b) return;
    std::cout << "Expected: " << b << ", got: " << a << std::endl;
    exit(1);
}

extern "C" void assertFloatEquals(double a, double b) {
    if (a == b) return;
    std::cout << "Expected: " << b << ", got: " << a << std::endl;
    exit(1);
}

struct MyInit {
    MyInit() {
        test_main();
    }
} my_init;

#define perf_argument 10000

static void BM_Target(benchmark::State& state) {
    for (auto _ : state)
        performance_target(perf_argument);
}
BENCHMARK(BM_Target);

static void BM_Gold(benchmark::State& state) {
    for (auto _ : state)
        performance_gold(perf_argument);
}
BENCHMARK(BM_Gold);

BENCHMARK_MAIN();