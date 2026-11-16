package com.example.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class NaiveLockTest extends RedisContainerSupport {

    static final Duration LEASE = Duration.ofMillis(200);

    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void emptyRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void crashBetweenSetnxAndExpire_leavesALockThatNeverExpires() {
        NaiveRedisLock crashesMidway = new NaiveRedisLock(redis, () -> {
            throw new IllegalStateException("process killed (deploy, OOM, kill -9)");
        });

        assertThatThrownBy(() -> crashesMidway.tryLock("lock:report", LEASE));

        assertThat(redis.getExpire("lock:report")).isEqualTo(-1L);                // no TTL: held forever
        assertThat(new NaiveRedisLock(redis).tryLock("lock:report", LEASE)).isFalse();
    }

    @Test
    void setNxPx_isOneCommand_soThereIsNoMomentWithoutAnExpiry() {
        new RedisLock(redis).tryLock("lock:report", LEASE).orElseThrow();

        assertThat(redis.getExpire("lock:report")).isNotEqualTo(-1L);
    }

    @Test
    void plainDel_afterYourLeaseExpired_deletesSomeoneElsesLock() {
        NaiveRedisLock lock = new NaiveRedisLock(redis);
        assertThat(lock.tryLock("lock:report", LEASE)).isTrue();        // A
        Leases.awaitExpiry(redis, "lock:report");                       // A is slow; its lease runs out
        assertThat(lock.tryLock("lock:report", LEASE)).isTrue();        // B takes the lock

        lock.unlock("lock:report");                                     // A finishes and "releases"

        // B's lock is gone, so C gets in while B is still working: two holders.
        assertThat(lock.tryLock("lock:report", LEASE)).isTrue();
    }

    @Test
    void checkAndDeleteScript_onlyReleasesYourOwnLock() {
        RedisLock lock = new RedisLock(redis);
        RedisLock.Lease a = lock.tryLock("lock:report", LEASE).orElseThrow();
        Leases.awaitExpiry(redis, "lock:report");
        RedisLock.Lease b = lock.tryLock("lock:report", Duration.ofSeconds(10)).orElseThrow();

        assertThat(lock.release(a)).isFalse();                          // not A's lock any more: untouched
        assertThat(lock.tryLock("lock:report", LEASE)).isEmpty();       // C still has to wait for B
        assertThat(lock.release(b)).isTrue();
    }
}
