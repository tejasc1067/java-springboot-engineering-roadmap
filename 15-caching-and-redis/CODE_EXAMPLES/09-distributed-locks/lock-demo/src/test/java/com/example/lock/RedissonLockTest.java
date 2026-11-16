package com.example.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/** Redisson used directly, two clients standing for two app instances. */
class RedissonLockTest extends RedisContainerSupport {

    RedissonClient instanceA;
    RedissonClient instanceB;

    @BeforeEach
    void connect() {
        instanceA = client();
        instanceB = client();
        instanceA.getKeys().flushall();
    }

    @AfterEach
    void disconnect() {
        if (!instanceA.isShutdown()) {
            instanceA.shutdown();
        }
        instanceB.shutdown();
    }

    static RedissonClient client() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        // Default is 30 s; 1 s keeps the test short. The watchdog renews every timeout / 3.
        config.setLockWatchdogTimeout(1_000);
        return Redisson.create(config);
    }

    @Test
    void watchdog_keepsExtendingTheLease_whileTheHolderIsAlive() throws Exception {
        RLock lockOnA = instanceA.getLock("lock:report");
        lockOnA.lock();                                         // no lease time given: the watchdog takes over

        Thread.sleep(2_500);                                    // well past the 1 s lease

        assertThat(lockOnA.isLocked()).isTrue();
        assertThat(instanceB.getLock("lock:report").tryLock()).isFalse();
        lockOnA.unlock();
    }

    @Test
    void holderDies_theWatchdogStops_andTheLockExpires() throws Exception {
        instanceA.getLock("lock:report").lock();

        instanceA.shutdown();                                   // the instance crashes without unlocking

        assertThat(instanceB.getLock("lock:report").tryLock(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void anExplicitLeaseTime_turnsTheWatchdogOff() throws Exception {
        RLock lockOnA = instanceA.getLock("lock:report");
        lockOnA.lock(300, TimeUnit.MILLISECONDS);

        // Still "working", but the lock is gone after 300 ms: B gets in.
        assertThat(instanceB.getLock("lock:report").tryLock(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void onlyTheOwnerCanUnlock() throws Exception {
        RLock lockOnA = instanceA.getLock("lock:report");
        lockOnA.lock();

        CompletableFuture<Void> otherThread = CompletableFuture.runAsync(() -> instanceB.getLock("lock:report").unlock());

        assertThatThrownBy(otherThread::join).hasCauseInstanceOf(IllegalMonitorStateException.class);
        assertThat(lockOnA.isLocked()).isTrue();
        lockOnA.unlock();
    }
}
