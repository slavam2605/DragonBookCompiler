cmake_minimum_required(VERSION 3.12)
project(benchmark_helper)

set(CMAKE_CXX_STANDARD 20)

set(benchmark_DIR "${CMAKE_SOURCE_DIR}/benchmark/build")
find_package(benchmark REQUIRED)

add_library(benchmark_helper_obj OBJECT main.cpp)
target_link_libraries(benchmark_helper_obj PRIVATE benchmark::benchmark)

set(BENCHMARK_HELPER_O "${CMAKE_CURRENT_BINARY_DIR}/benchmark_helper.o")
add_custom_command(
        OUTPUT "${BENCHMARK_HELPER_O}"
        COMMAND ${CMAKE_LINKER} -r -o "${BENCHMARK_HELPER_O}" $<TARGET_OBJECTS:benchmark_helper_obj> $<TARGET_FILE:benchmark::benchmark>
        DEPENDS benchmark_helper_obj benchmark::benchmark
        VERBATIM
)

add_custom_target(benchmark_helper ALL DEPENDS "${BENCHMARK_HELPER_O}")