bool is_prime_gold(long long n) {
    if (n <= 1) {
        return false;
    }
    for (int i = 2; i <= n / 2; i++) {
        if (n % i == 0) {
            return false;
        }
    }
    return true;
}

long long count_primes_gold(long long from, long long to) {
    long long count = 0;
    for (long long n = from; n <= to; n++) {
        if (is_prime_gold(n)) {
            count += 1;
        }
    }
    return count;
}

extern "C" long long performance_gold(long long n) {
    return count_primes_gold(0, n);
}