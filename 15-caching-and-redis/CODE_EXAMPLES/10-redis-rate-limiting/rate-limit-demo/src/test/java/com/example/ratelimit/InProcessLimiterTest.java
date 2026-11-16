package com.example.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

/**
 * The limit is "5 requests per minute per client". The service runs as 2 instances behind a load balancer,
 * which spreads the client's requests across them.
 */
class InProcessLimiterTest extends RedisTestBase {

    @Test
    void inProcessLimiter_eachInstanceCountsAlone_soTwoInstancesAllowTwiceTheLimit() {
        RateLimiterConfig fivePerMinute = RateLimiterConfig.custom()
                .limitForPeriod(5).limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build();
        RateLimiter instanceA = RateLimiter.of("client-42", fivePerMinute);   // lives in instance A's memory
        RateLimiter instanceB = RateLimiter.of("client-42", fivePerMinute);   // lives in instance B's memory

        long allowed = IntStream.range(0, 20)
                .mapToObj(i -> i % 2 == 0 ? instanceA : instanceB)            // round-robin load balancer
                .filter(RateLimiter::acquirePermission)
                .count();

        assertThat(allowed).isEqualTo(10);   // limit was 5
    }

    @Test
    void redisLimiter_bothInstancesShareOneCounter_soTheLimitHolds() {
        FixedWindowLimiter instanceA = new FixedWindowLimiter(redis, 5, Duration.ofMinutes(1));
        FixedWindowLimiter instanceB = new FixedWindowLimiter(redis, 5, Duration.ofMinutes(1));
        long now = 1_000_000L;

        long allowed = IntStream.range(0, 20)
                .mapToObj(i -> i % 2 == 0 ? instanceA : instanceB)
                .filter(limiter -> limiter.tryAcquire("client-42", now))
                .count();

        assertThat(allowed).isEqualTo(5);
    }
}
