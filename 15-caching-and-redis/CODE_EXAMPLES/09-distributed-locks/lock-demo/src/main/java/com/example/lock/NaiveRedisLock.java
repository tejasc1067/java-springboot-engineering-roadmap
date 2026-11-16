package com.example.lock;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The lock most people write first. Both methods look right and both are broken:
 * tryLock takes the lock and sets its expiry in two separate commands, and unlock deletes
 * the key without checking whose lock it is.
 */
public class NaiveRedisLock {

    private final StringRedisTemplate redis;
    private final Runnable betweenSetAndExpire;

    public NaiveRedisLock(StringRedisTemplate redis) {
        this(redis, () -> { });
    }

    /** The hook lets a test crash the process at the worst moment. */
    NaiveRedisLock(StringRedisTemplate redis, Runnable betweenSetAndExpire) {
        this.redis = redis;
        this.betweenSetAndExpire = betweenSetAndExpire;
    }

    public boolean tryLock(String key, Duration lease) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "locked");    // SETNX
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        betweenSetAndExpire.run();
        redis.expire(key, lease);                                             // EXPIRE: a second command
        return true;
    }

    public void unlock(String key) {
        redis.delete(key);                                                    // DEL: whoever's lock it is now
    }
}
