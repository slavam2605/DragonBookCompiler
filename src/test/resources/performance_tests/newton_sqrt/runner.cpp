#include <cmath>

double abs_float_gold(double x) {
    if (x < 0.0) {
        return -x;
    }
    return x;
}

double sqrt_newton_gold(double x) {
    if (x <= 0.0) {
        return 0.0;
    }

    double guess = x / 2.0;
    double epsilon = 0.000001;

    for (int i = 0; i < 20; i++) {
        double next_guess = (guess + x / guess) / 2.0;
        double diff = abs_float_gold(next_guess - guess);
        if (diff < epsilon) {
            return next_guess;
        }
        guess = next_guess;
    }

    return guess;
}

double compute_sqrt_sum_gold(long long n) {
    double sum = 0.0;
    for (long long i = 1; i <= n; i++) {
        double val = (double)i;
        sum += sqrt_newton_gold(val);
    }
    return sum;
}

extern "C" long long performance_gold(long long n) {
    double result = compute_sqrt_sum_gold(n);
    return (long long)result;
}