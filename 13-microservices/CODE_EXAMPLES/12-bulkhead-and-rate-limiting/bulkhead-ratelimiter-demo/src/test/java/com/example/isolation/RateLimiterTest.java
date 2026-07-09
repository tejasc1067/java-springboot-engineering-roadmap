package com.example.isolation;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A rate limiter caps how many calls are allowed per unit of TIME, shedding the excess.
 * Use it to protect a downstream from being overwhelmed, or to enforce a quota. Unlike
 * a bulkhead (which limits concurrency) it limits throughput regardless of how long
 * each call takes.
 */
class RateLimiterTest {

    @Test
    void allowsUpToTheLimitPerPeriodThenRejects() {
        RateLimiter limiter = RateLimiter.of("inventory", RateLimiterConfig.custom()
                .limitForPeriod(3)                          // 3 calls...
                .limitRefreshPeriod(Duration.ofSeconds(1))  // ...per second
                .timeoutDuration(Duration.ZERO)             // don't block waiting for a permit
                .build());
        Supplier<String> call = RateLimiter.decorateSupplier(limiter, () -> "ok");

        assertThat(call.get()).isEqualTo("ok");
        assertThat(call.get()).isEqualTo("ok");
        assertThat(call.get()).isEqualTo("ok");           // 3 permitted in this period

        assertThatThrownBy(call::get).isInstanceOf(RequestNotPermitted.class); // 4th shed
    }

    @Test
    void permitsRefillAfterTheRefreshPeriod() throws InterruptedException {
        RateLimiter limiter = RateLimiter.of("inventory", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMillis(300))
                .timeoutDuration(Duration.ZERO)
                .build());
        Supplier<String> call = RateLimiter.decorateSupplier(limiter, () -> "ok");

        assertThat(call.get()).isEqualTo("ok");            // 1 permit used
        assertThatThrownBy(call::get).isInstanceOf(RequestNotPermitted.class); // exhausted

        Thread.sleep(350);                                  // wait out the refresh period

        assertThat(call.get()).isEqualTo("ok");            // permits replenished
    }
}
