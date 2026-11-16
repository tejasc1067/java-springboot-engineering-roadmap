package com.example.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * BROKEN: the most common Redis rate limiter, written as two separate commands.
 * The window is "the key's lifetime": INCR creates the key, EXPIRE ends the window.
 */
public class IncrThenExpireLimiter {

    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;
    private Runnable betweenIncrAndExpire = () -> { };

    public IncrThenExpireLimiter(StringRedisTemplate redis, int limit, Duration window) {
        this.redis = redis;
        this.limit = limit;
        this.window = window;
    }

    /** Test hook: simulates the app dying (deploy, OOM, timeout) between the two commands. */
    public void setBetweenIncrAndExpire(Runnable hook) {
        this.betweenIncrAndExpire = hook;
    }

    public boolean tryAcquire(String clientId) {
        String key = "rl:naive:" + clientId;
        Long count = redis.opsForValue().increment(key);   // INCR
        if (count == 1) {
            betweenIncrAndExpire.run();
            redis.expire(key, window);                        // EXPIRE - never runs if we died above
        }
        return count <= limit;
    }
}
