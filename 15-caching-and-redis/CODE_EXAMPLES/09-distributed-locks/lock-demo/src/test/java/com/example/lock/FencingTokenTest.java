package com.example.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Even a correct lock can't stop this: A's lease expires while A is paused (a long GC, a slow call),
 * B gets the lock, and then A wakes up still believing it holds the lock.
 */
@SpringBootTest
class FencingTokenTest extends RedisContainerSupport {

    @Autowired StringRedisTemplate redis;

    RedisLock lock;
    ReportStorage storage;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        lock = new RedisLock(redis);
        storage = new ReportStorage();
    }

    @Test
    void withoutFencing_thePausedHolderOverwritesTheNewHoldersWork() {
        RedisLock.Lease a = lock.tryLock("lock:report", Duration.ofMillis(200)).orElseThrow();
        Leases.awaitExpiry(redis, "lock:report");                      // A is paused past its lease

        lock.tryLock("lock:report", Duration.ofSeconds(10)).orElseThrow();
        storage.write("report by B");

        assertThat(redis.opsForValue().get("lock:report")).isNotEqualTo(a.owner());   // A no longer holds it...
        storage.write("report by A (stale)");                                         // ...but wakes up and writes

        assertThat(storage.content()).isEqualTo("report by A (stale)");
    }

    @Test
    void withFencing_storageRejectsTheOlderToken() {
        RedisLock.Lease a = lock.tryLock("lock:report", Duration.ofMillis(200)).orElseThrow();
        Leases.awaitExpiry(redis, "lock:report");

        RedisLock.Lease b = lock.tryLock("lock:report", Duration.ofSeconds(10)).orElseThrow();
        assertThat(b.fencingToken()).isGreaterThan(a.fencingToken());

        assertThat(storage.write(b.fencingToken(), "report by B")).isTrue();
        assertThat(storage.write(a.fencingToken(), "report by A (stale)")).isFalse();
        assertThat(storage.content()).isEqualTo("report by B");
    }
}
