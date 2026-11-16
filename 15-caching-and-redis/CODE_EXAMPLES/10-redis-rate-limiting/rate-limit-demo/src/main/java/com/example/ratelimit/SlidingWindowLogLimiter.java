package com.example.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Sliding window log: at most {@code limit} requests in ANY {@code window}-long period.
 * Each allowed request is stored in a sorted set, scored by its timestamp.
 * The three steps (drop old entries, count, add) must be atomic, or two requests
 * can both count 4 and both be let in as the 5th - so they run as one Lua script.
 */
public class SlidingWindowLogLimiter {

    private static final RedisScript<Long> SLIDING_LOG = RedisScript.of("""
            local key, now, window, limit, member = KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3]), ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            if redis.call('ZCARD', key) < limit then
              redis.call('ZADD', key, now, member)
              redis.call('PEXPIRE', key, window)
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;

    public SlidingWindowLogLimiter(StringRedisTemplate redis, int limit, Duration window) {
        this.redis = redis;
        this.limit = limit;
        this.window = window;
    }

    public boolean tryAcquire(String clientId, long nowMillis) {
        // Member must be unique: two requests in the same millisecond are two entries, not one.
        String member = nowMillis + "-" + UUID.randomUUID();
        Long allowed = redis.execute(SLIDING_LOG, List.of("rl:sliding:" + clientId),
                String.valueOf(nowMillis), String.valueOf(window.toMillis()), String.valueOf(limit), member);
        return allowed == 1;
    }
}
