#include <iostream>
#include <climits>
#include <random>

extern "C" long long calculate_div_p2(long long n);
extern "C" long long calculate_div(long long n);

long long calculate_div_gold_p2(long long n) {
    long long a = n / 1 + n / 2 + n / 4 + n / 8 + n / 1024 + n / 131072 + n / 1048576;
    long long b = n % 1 + n % 2 + n % 4 + n % 8 + n % 1024 + n % 131072 + n % 1048576;
    long long c = n / -1 + n / -2 + n / -4 + n / -8 + n / -1024 + n / -131072 + n / -1048576 + n / INT64_MIN;
    long long d = n % -1 + n % -2 + n % -4 + n % -8 + n % -1024 + n % -131072 + n % -1048576 + n % INT64_MIN;
    return a + b + 3 * (c + d) / 7;
}

long long calculate_div_gold(long long n) {
    long long a = n / 3 + n / 5 + n / 7 + n / 11 + n / 13 + n / 97 + n / 1009 + n / 128712 + n / 2147483647LL + n / 8589935681LL + n / 489133282872437279LL;
    long long b = n % 3 + n % 5 + n % 7 + n % 11 + n % 13 + n % 97 + n % 1009 + n % 128712 + n % 2147483647LL + n % 8589935681LL + n % 489133282872437279LL;
    long long c = n / -3 + n / -5 + n / -7 + n / -11 + n / -13 + n / -97 + n / -1009 + n / -128712 + n / -2147483647LL + n / -8589935681LL + n / -489133282872437279LL;
    long long d = n % -3 + n % -5 + n % -7 + n % -11 + n % -13 + n % -97 + n % -1009 + n % -128712 + n % -2147483647LL + n % -8589935681LL + n % -489133282872437279LL;
    return a + b + 2 * (c + d) / 5;
}

extern "C" long long performance_gold(long long n) {
    long long result = 0;
    for (long long i = 0; i < n; i++) {
        result += calculate_div_gold_p2(i * i) + calculate_div_gold(i * i + 1);
    }
    return result;
}

class MyRunnerInit {
    void test_p2(long long value) {
        long long expected = calculate_div_p2(value);
        long long actual = calculate_div_gold_p2(value);
        if (actual != expected) {
            std::cout << "Wrong value for calculate_div_p2(" << value << "): " <<
                "expected: " << expected << " actual: " << actual << std::endl;
            exit(1);
        }
    }

    void test(long long value) {
        long long expected = calculate_div(value);
        long long actual = calculate_div_gold(value);
        if (actual != expected) {
            std::cout << "Wrong value for calculate_div(" << value << "): " <<
                "expected: " << expected << " actual: " << actual << std::endl;
            exit(1);
        }
    }

    void run_log_tests(int64_t start, int64_t end, size_t count) {
        bool negative = (start < 0);
        uint64_t absStart = std::abs(start);
        uint64_t absEnd = std::abs(end);

        // Generate exponentially spaced magnitudes by interpolating in log space
        for (size_t i = 0; i < count; i++) {
            double t = static_cast<double>(i) / count;
            // Exponential interpolation: mag = start * (end/start)^t
            double logMag = std::log(absStart) + t * (std::log(absEnd) - std::log(absStart));
            uint64_t mag = static_cast<uint64_t>(std::exp(logMag));

            // Clamp and apply sign
            mag = std::min(std::max(mag, absStart), absEnd);
            test(negative ? -static_cast<int64_t>(mag) : mag);
        }

    }

    void test_both(long long value) {
        test_p2(value);
        test(value);
    }

public:
    MyRunnerInit() {
        test_both(0); test_both(1); test_both(-1); test_both(2); test_both(-2);
        test_both(INT64_MIN); test_both(INT64_MIN + 1); test_both(INT64_MAX); test_both(INT64_MAX - 1);
        for (int shift = 0; shift < 64; shift++) {
            long long pow2 = 1 << shift;
            test_both(pow2); test_both(pow2 + 1); test_both(pow2 - 1);
            test_both(-pow2); test_both(-pow2 + 1); test_both(-pow2 - 1);
        }
        run_log_tests(1, INT64_MAX, 10000000);
        run_log_tests(INT64_MIN, -1, 10000000);
        for (int i = -10000000; i <= 10000000; i++) {
            test_both(i);
        }
        std::mt19937_64 rng(42);
        std::uniform_int_distribution<int64_t> dist(INT64_MIN, INT64_MAX);
        for (int i = 0; i < 10000000; i++) {
            test_both(dist(rng));
        }
    }
} myRunnerInit;