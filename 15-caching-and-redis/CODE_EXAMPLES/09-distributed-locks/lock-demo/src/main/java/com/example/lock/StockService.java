package com.example.lock;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;

/** One per app instance. The stock count lives in Redis, shared by every instance. */
public class StockService {

    private final StringRedisTemplate redis;
    private final RedisLock lock;

    public StockService(StringRedisTemplate redis) {
        this.redis = redis;
        this.lock = new RedisLock(redis);
    }

    /**
     * synchronized locks on THIS object, which exists in one JVM. Another instance has its own object
     * and its own lock, so the check-then-act below still races across instances.
     * The afterRead hook lets a test hold one sale between its read and its write.
     */
    public synchronized boolean sellLocallySynchronized(String sku, Runnable afterRead) {
        int stock = Integer.parseInt(redis.opsForValue().get("stock:" + sku));
        if (stock <= 0) {
            return false;
        }
        afterRead.run();
        redis.opsForValue().set("stock:" + sku, String.valueOf(stock - 1));
        return true;
    }

    /** Same check-then-act, guarded by a lock every instance can see. */
    public boolean sellWithRedisLock(String sku, Runnable afterRead) {
        Optional<RedisLock.Lease> lease = lock.tryLock("lock:stock:" + sku, Duration.ofSeconds(10));
        if (lease.isEmpty()) {
            return false;   // a real service would wait and retry, or answer "busy, try again"
        }
        try {
            return sellLocallySynchronized(sku, afterRead);
        } finally {
            lock.release(lease.get());
        }
    }
}
