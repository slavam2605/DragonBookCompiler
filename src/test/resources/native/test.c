#include <stdio.h>
#include <stdlib.h>

extern long long test_main(long long);

void assertEquals(long long a, long long b) {
    if (a == b) return;
    printf("assertEquals failed: %lld != %lld", a, b);
    exit(1);
}

void assertStaticEquals(long long a, long long b) {
    if (a == b) return;
    printf("assertStaticEquals failed: %lld != %lld", a, b);
    exit(1);
}

void assertFloatEquals(double a, double b) {
    if (a == b) return;
    printf("assertEquals failed: %f != %f", a, b);
    exit(1);
}

void assertStaticFloatEquals(double a, double b) {
    if (a == b) return;
    printf("assertStaticEquals failed: %f != %f", a, b);
    exit(1);
}

void assertStaticUnknown(long long x) {}

void print_float(double x) {
    printf("%f\n", x);
}

void println() {
    printf("\n");
}

long long undef(long long x) {
    return x;
}

int main() {
    printf("%lld\n", test_main(7));
    return 0;
}