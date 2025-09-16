#include <stdio.h>
#include <stdlib.h>

extern long long fibonacci(long long);

long long gold_fibonacci(long long n) {
    if (n == 0) return 0;
    if (n == 1) return 1;

    long long a = 0, b = 1;
    for (long long i = 2; i <= n; ++i) {
        long long c = a + b;
        a = b;
        b = c;
    }
    return b;
}


int main() {
    for (long long i = 0; i <= 94; i = i + 1) {
        long long test_value = fibonacci(i);
        long long gold_value = gold_fibonacci(i);
        if (test_value != gold_value) {
            printf("Error: fibonacci(%lld) = %lld, expected %lld\n", i, test_value, gold_value);
            return 1;
        } else {
            printf("Correct: fibonacci(%lld) = %lld\n", i, test_value);
        }
    }
    return 0;
}