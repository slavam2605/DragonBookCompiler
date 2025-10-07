#!/usr/bin/env bash
set -euo pipefail
trap 'echo "[setup_benchmark] Error on line $LINENO. Exiting." >&2' ERR

# Always run from the script's directory
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$SCRIPT_DIR"

# 1) Basic check that required tools exist
REQUIRED_TOOLS=(git cmake)
for tool in "${REQUIRED_TOOLS[@]}"; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Error: required tool '$tool' is not installed or not in PATH." >&2
    exit 1
  fi
done

# 2) Clone the benchmark repository only if not yet cloned
if [ ! -d "benchmark/.git" ]; then
  echo "[setup_benchmark] Cloning google/benchmark..."
  git clone https://github.com/google/benchmark.git benchmark
else
  echo "[setup_benchmark] 'benchmark' already cloned. Skipping clone."
fi

# 3) Configure and build (always run; CMake will do up-to-date no-op if already built)
echo "[setup_benchmark] Configuring and building google/benchmark (up-to-date if already built)..."
cd benchmark

declare -r BUILD_DIR="build"
cmake -E make_directory "$BUILD_DIR"

cmake -E chdir "$BUILD_DIR" cmake -DBENCHMARK_DOWNLOAD_DEPENDENCIES=on -DCMAKE_BUILD_TYPE=Release ../

cmake --build "$BUILD_DIR" --config Release

# Ensure we return to the script directory after the build block
cd "$SCRIPT_DIR" || exit 1
echo "[setup_benchmark] Build completed."

# 4) Build the benchmark_helper CMake project (Release)
echo "[setup_benchmark] Configuring and building benchmark_helper project (Release)..."
declare -r NATIVE_BUILD_DIR="build"
cmake -E make_directory "$NATIVE_BUILD_DIR"
rm -f "$NATIVE_BUILD_DIR/benchmark_helper.o" 2>/dev/null || true
cmake -E chdir "$NATIVE_BUILD_DIR" cmake -DCMAKE_BUILD_TYPE=Release ../
cmake --build "$NATIVE_BUILD_DIR" --config Release

# Copy the built object to src/test/resources/native (overwrite if exists)
declare -r DEST_DIR="../src/test/resources/native"
cmake -E make_directory "$DEST_DIR"
cp -f "$NATIVE_BUILD_DIR/benchmark_helper.o" "$DEST_DIR/benchmark_helper.o"
echo "[setup_benchmark] Copied $NATIVE_BUILD_DIR/benchmark_helper.o -> $DEST_DIR/benchmark_helper.o"

echo "[setup_benchmark] Native helper build completed."