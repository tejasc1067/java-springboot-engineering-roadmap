package com.example.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class SlidingWindowTest extends RedisTestBase {

    @Test
    void slidingWindow_noBurstAtTheEdge_theLast60SecondsAlwaysHoldAtMost5() {
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(redis, 5, Duration.ofSeconds(60));

        assertThat(countAllowed(limiter, 5, 59_900L)).isEqualTo(5);
        assertThat(countAllowed(limiter, 5, 60_100L)).isZero();      // the 5 from 0.2s ago still count
        assertThat(countAllowed(limiter, 5, 119_899L)).isZero();     // still inside their 60s
        assertThat(countAllowed(limiter, 5, 119_901L)).isEqualTo(5); // 60s after them, they've slid out
    }

    @Test
    void slidingWindow_storesOneSortedSetEntryPerAllowedRequest() {
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(redis, 5, Duration.ofSeconds(60));

        countAllowed(limiter, 8, 10_000L);

        // The cost of precision: memory grows with the limit (5 entries here; 10,000 for "10,000/hour").
        assertThat(redis.opsForZSet().zCard("rl:sliding:client-42")).isEqualTo(5L);
    }

    @Test
    void separateCalls_twoConcurrentRequestsBothSee4_andBothGetIn() throws Exception {
        SlidingWindowSeparateCallsBroken limiter = new SlidingWindowSeparateCallsBroken(redis, 5, Duration.ofSeconds(60));
        IntStream.range(0, 4).forEach(i -> limiter.tryAcquire("client-42", 10_000L));   // 4 of 5 used

        CyclicBarrier bothHaveCounted = new CyclicBarrier(2);
        limiter.setBetweenCountAndAdd(() -> await(bothHaveCounted));
        long allowed;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> limiter.tryAcquire("client-42", 10_000L));
            Future<Boolean> second = pool.submit(() -> limiter.tryAcquire("client-42", 10_000L));
            allowed = 4 + (get(first) ? 1 : 0) + (get(second) ? 1 : 0);
        }

        assertThat(allowed).isEqualTo(6);   // limit 5
    }

    @Test
    void luaScript_concurrentRequests_neverExceedTheLimit() throws Exception {
        SlidingWindowLogLimiter limiter = new SlidingWindowLogLimiter(redis, 5, Duration.ofSeconds(60));
        List<Callable<Boolean>> requests = new ArrayList<>();
        IntStream.range(0, 50).forEach(i -> requests.add(() -> limiter.tryAcquire("client-42", 10_000L)));

        long allowed;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            allowed = pool.invokeAll(requests).stream().filter(SlidingWindowTest::get).count();
        }

        assertThat(allowed).isEqualTo(5);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long countAllowed(SlidingWindowLogLimiter limiter, int requests, long nowMillis) {
        return IntStream.range(0, requests).filter(i -> limiter.tryAcquire("client-42", nowMillis)).count();
    }
}
