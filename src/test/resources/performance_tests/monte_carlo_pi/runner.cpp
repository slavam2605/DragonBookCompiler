long long lcg_next_gold(long long seed) {
    // Linear Congruential Generator: (a * seed + c) % m
    long long a = 1103515245LL;
    long long c = 12345LL;
    long long m = 2147483647LL;  // 2^31 - 1
    return (a * seed + c) % m;
}

double random_float_gold(long long seed) {
    // Convert int [0, 2^31-1] to double [0.0, 1.0]
    return (double)seed / 2147483647.0;
}

double estimate_pi_gold(long long iterations, long long seed) {
    long long inside = 0;
    long long current_seed = seed;

    for (long long i = 0; i < iterations; i++) {
        // Generate random x coordinate
        current_seed = lcg_next_gold(current_seed);
        double x = random_float_gold(current_seed);

        // Generate random y coordinate
        current_seed = lcg_next_gold(current_seed);
        double y = random_float_gold(current_seed);

        // Check if point is inside quarter circle
        double distance_squared = x * x + y * y;
        if (distance_squared <= 1.0) {
            inside += 1;
        }
    }

    return 4.0 * (double)inside / (double)iterations;
}

double run_monte_carlo_trials_gold(long long iterations_per_trial, long long num_trials) {
    double sum = 0.0;
    for (long long trial = 0; trial < num_trials; trial++) {
        long long seed = 12345 + trial * 1000;
        double pi_estimate = estimate_pi_gold(iterations_per_trial, seed);
        sum += pi_estimate;
    }
    return sum / (double)num_trials;
}

extern "C" long long performance_gold(long long n) {
    long long num_trials = n / 200;
    double avg_pi = run_monte_carlo_trials_gold(n, num_trials);
    return (long long)(avg_pi * 1000000.0);
}
