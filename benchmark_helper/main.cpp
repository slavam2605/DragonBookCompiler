#include <benchmark/benchmark.h>

extern "C" long long performance_target(long long);
extern "C" long long performance_gold(long long);

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