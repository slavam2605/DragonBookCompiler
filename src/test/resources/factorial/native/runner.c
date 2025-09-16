#include <stdio.h>
#include <stdlib.h>

extern long long factorial(long long);

long long gold_factorial(long long n) {
    if (n < 0) {
        return 0; // undefined for negative inputs; returning 0
    }
    long long result = 1;
    for (long long i = 2; i <= n; ++i) {
        result *= i;
    }
    return result;
}

int main() {
    for (long long i = 0; i <= 20; i = i + 1) {
        long long test_value = factorial(i);
        long long gold_value = gold_factorial(i);
        if (test_value != gold_value) {
            printf("Error: factorial(%lld) = %lld, expected %lld\n", i, test_value, gold_value);
            return 1;
        } else {
            printf("Correct: factorial(%lld) = %lld\n", i, test_value);
        }
    }
    return 0;
}