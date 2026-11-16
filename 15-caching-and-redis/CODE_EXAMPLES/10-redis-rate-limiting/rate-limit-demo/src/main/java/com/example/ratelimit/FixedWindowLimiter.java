package com.example.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Fixed window: at most {@code limit} requests per client per window (e.g. per minute, 12:00:00-12:00:59).
 * INCR and EXPIRE run inside one Lua script, which Redis executes atomically:
 * no other command runs in between, and there is no "between" for the app to die in.
 */
public class FixedWindowLimiter {

    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;

    public FixedWindowLimiter(StringRedisTemplate redis, int limit, Duration window) {
        this.redis = redis;
        this.limit = limit;
        this.window = window;
    }

    /** {@code nowMillis} is passed in so tests can stand exactly on a window edge. */
    public boolean tryAcquire(String clientId, long nowMillis) {
        long windowNumber = nowMillis / window.toMillis();
        String key = "rl:fixed:" + clientId + ":" + windowNumber;
        Long count = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
        return count <= limit;
    }
}
