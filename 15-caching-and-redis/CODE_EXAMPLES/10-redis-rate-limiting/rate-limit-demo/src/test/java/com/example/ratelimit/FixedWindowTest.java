package com.example.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class FixedWindowTest extends RedisTestBase {

    @Test
    void incrThenExpire_appDiesBetweenTheTwoCommands_keyNeverExpires_clientBlockedForever() {
        IncrThenExpireLimiter limiter = new IncrThenExpireLimiter(redis, 5, Duration.ofSeconds(60));
        limiter.setBetweenIncrAndExpire(() -> { throw new IllegalStateException("instance killed mid-request"); });

        assertThatThrownBy(() -> limiter.tryAcquire("client-42")).isInstanceOf(IllegalStateException.class);
        limiter.setBetweenIncrAndExpire(() -> { });   // the app is healthy again

        // TTL -1 = "this key exists and never expires". The window never ends.
        assertThat(redis.getExpire("rl:naive:client-42")).isEqualTo(-1L);

        IntStream.range(0, 4).forEach(i -> limiter.tryAcquire("client-42"));   // requests 2-5 allowed
        assertThat(limiter.tryAcquire("client-42")).isFalse();                  // 6th rejected...
        // ...and every request after it, today, tomorrow, next month: count only grows, nothing resets it.
        assertThat(redis.getExpire("rl:naive:client-42")).isEqualTo(-1L);
    }

    @Test
    void luaScript_incrAndExpireAreAtomic_soTheKeyAlwaysHasATtl() {
        FixedWindowLimiter limiter = new FixedWindowLimiter(redis, 5, Duration.ofSeconds(60));
        long now = 120_000L;   // window number 2

        limiter.tryAcquire("client-42", now);

        assertThat(redis.getExpire("rl:fixed:client-42:2")).isBetween(1L, 60L);
    }

    @Test
    void fixedWindow_allowsTheLimitPerWindow_andResetsInTheNextWindow() {
        FixedWindowLimiter limiter = new FixedWindowLimiter(redis, 5, Duration.ofSeconds(60));

        assertThat(countAllowed(limiter, 6, 10_000L)).isEqualTo(5);   // 6 requests at 00:10 -> 5 allowed
        assertThat(countAllowed(limiter, 1, 61_000L)).isEqualTo(1);   // next minute -> fresh counter
    }

    @Test
    void fixedWindow_boundaryBurst_twiceTheLimitInOneSecond() {
        FixedWindowLimiter limiter = new FixedWindowLimiter(redis, 5, Duration.ofSeconds(60));

        long allowedJustBeforeTheEdge = countAllowed(limiter, 5, 59_900L);   // 00:59.9, window 0
        long allowedJustAfterTheEdge = countAllowed(limiter, 5, 60_100L);    // 01:00.1, window 1

        // "5 per minute", yet 10 requests got through within 0.2 seconds.
        assertThat(allowedJustBeforeTheEdge + allowedJustAfterTheEdge).isEqualTo(10);
    }

    private static long countAllowed(FixedWindowLimiter limiter, int requests, long nowMillis) {
        return IntStream.range(0, requests).filter(i -> limiter.tryAcquire("client-42", nowMillis)).count();
    }
}
