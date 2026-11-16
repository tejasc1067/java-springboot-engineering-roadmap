package com.example.ratelimit;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * BROKEN: the same sliding-window algorithm as {@link SlidingWindowLogLimiter}, but as three separate
 * Redis calls. Each call is atomic on its own; the sequence isn't. Two requests can both count 4,
 * both decide "under 5", and both be added.
 */
public class SlidingWindowSeparateCallsBroken {

    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;
    private Runnable betweenCountAndAdd = () -> { };

    public SlidingWindowSeparateCallsBroken(StringRedisTemplate redis, int limit, Duration window) {
        this.redis = redis;
        this.limit = limit;
        this.window = window;
    }

    /** Test hook: lets a test hold two requests at the same point, the way real concurrency sometimes does. */
    public void setBetweenCountAndAdd(Runnable hook) {
        this.betweenCountAndAdd = hook;
    }

    public boolean tryAcquire(String clientId, long nowMillis) {
        String key = "rl:sliding:" + clientId;
        redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, nowMillis - window.toMillis());
        Long count = redis.opsForZSet().zCard(key);
        if (count >= limit) {
            return false;
        }
        betweenCountAndAdd.run();
        redis.opsForZSet().add(key, nowMillis + "-" + UUID.randomUUID(), nowMillis);
        return true;
    }
}
