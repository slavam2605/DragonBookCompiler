#include <stdio.h>
#include <stdlib.h>

extern long long foo(long long);

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

void assertStaticUnknown(long long x) {}

long long undef(long long x) {
    return x;
}

int main() {
    printf("%lld\n", foo(7));
    return 0;
}