package com.example.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A single-Redis lock done carefully: SET NX PX in one command, a random owner id per acquisition,
 * release only if the key still holds that id, and a fencing token for the storage to check.
 */
public class RedisLock {

    // GET and DEL in one script: Redis runs a script atomically, so nobody can take the lock in between.
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Lease(String key, String owner, long fencingToken) { }

    public Optional<Lease> tryLock(String key, Duration lease) {
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, lease);   // SET key owner NX PX lease
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        // Every successful acquisition gets a bigger number than the one before.
        long token = redis.opsForValue().increment("fencing:" + key);
        return Optional.of(new Lease(key, owner, token));
    }

    /** Returns false when the lease had already expired and the lock belongs to someone else (or no one). */
    public boolean release(Lease lease) {
        return redis.execute(RELEASE, List.of(lease.key()), lease.owner()) == 1L;
    }
}
