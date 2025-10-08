long long calculate_div_gold_p2(long long n) {
    long long a = n / 1 + n / 2 + n / 4 + n / 8 + n / 1024 + n / 131072 + n / 1048576;
    long long b = n % 1 + n % 2 + n % 4 + n % 8 + n % 1024 + n % 131072 + n % 1048576;
    long long c = n / -1 + n / -2 + n / -4 + n / -8 + n / -1024 + n / -131072 + n / -1048576;
    long long d = n % -1 + n % -2 + n % -4 + n % -8 + n % -1024 + n % -131072 + n % -1048576;
    return a + b + 3 * (c + d) / 7;
}

extern "C" long long performance_gold(long long n) {
    long long result = 0;
    for (long long i = 0; i < n; i++) {
        result += calculate_div_gold_p2(i * i);
    }
    return result;
}