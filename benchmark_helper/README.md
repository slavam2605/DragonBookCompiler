# Benchmark Helper
This folder contains a small helper that is used to run performance tests for the compiled programs.
To use, run `./setup_benchmark.sh` script that will:
1. Clone `google/benchmark` library sources
2. Build the benchmark library
3. Build this helper
4. Copy the helper to the `native` folder in test resources