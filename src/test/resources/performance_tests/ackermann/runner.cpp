long long ackermann_gold(long long m, long long n) {
    if (m == 0) {
        return n + 1;
    }
    if (n == 0) {
        return ackermann_gold(m - 1, 1);
    }
    return ackermann_gold(m - 1, ackermann_gold(m, n - 1));
}

long long compute_ackermann_sum_gold(long long max_m, long long max_n) {
    long long sum = 0;
    for (long long m = 0; m <= max_m; m++) {
        for (long long n = 0; n <= max_n; n++) {
            sum += ackermann_gold(m, n);
        }
    }
    return sum;
}

extern "C" long long performance_gold(long long n) {
    long long max_m = n / 10000 * 3;
    long long max_n = n / 1250;
    return compute_ackermann_sum_gold(max_m, max_n);
}
