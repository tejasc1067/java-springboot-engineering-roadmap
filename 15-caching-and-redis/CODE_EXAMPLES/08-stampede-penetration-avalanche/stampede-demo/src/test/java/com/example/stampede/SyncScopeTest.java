package com.example.stampede;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * What "only one load" really covers. Two Cache objects stand for the same cache on two app instances;
 * cache.get(key, loader) is exactly the call @Cacheable(sync = true) makes.
 */
@SpringBootTest
class SyncScopeTest extends RedisContainerSupport {

    @Autowired PriceService prices;
    @Autowired SlowDatabase database;
    @Autowired RedisConnectionFactory connectionFactory;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        database.reset();
    }

    @Test
    void localCacheSync_isPerJvm_soTwoInstancesLoadTwice() throws Exception {
        Cache onInstanceA = new CaffeineCache("prices", Caffeine.newBuilder().build());
        Cache onInstanceB = new CaffeineCache("prices", Caffeine.newBuilder().build());

        Herd.run(20, database, alternating(onInstanceA, onInstanceB));

        assertThat(database.loads()).isEqualTo(2);
    }

    @Test
    void lockingRedisWriter_holdsBackOtherInstancesToo() throws Exception {
        Cache onInstanceA = lockingRedisCache();
        Cache onInstanceB = lockingRedisCache();

        Herd.run(20, database, alternating(onInstanceA, onInstanceB));

        assertThat(database.loads()).isEqualTo(1);
    }

    @Test
    void lockingRedisWriter_locksTheWholeCache_soOtherKeysWaitToo() throws Exception {
        prices.priceSyncWithLockingWriter(3);                     // product 3 is already cached
        database.reset();

        CountDownLatch gate = database.holdLoads();
        CompletableFuture<Long> slowKey = CompletableFuture.supplyAsync(() -> prices.priceSyncWithLockingWriter(1));
        waitForLoads(1);

        CompletableFuture<Long> otherKey = CompletableFuture.supplyAsync(() -> prices.priceSyncWithLockingWriter(2));
        CompletableFuture<Long> cachedKey = CompletableFuture.supplyAsync(() -> prices.priceSyncWithLockingWriter(3));
        Thread.sleep(300);
        assertThat(database.loads()).as("key 2 hasn't started loading").isEqualTo(1);
        assertThat(otherKey).as("a miss on another key waits for key 1's lock").isNotDone();
        assertThat(cachedKey).as("even a hit waits for key 1's lock").isNotDone();

        gate.countDown();
        assertThat(slowKey.get()).isEqualTo(100L);
        assertThat(otherKey.get()).isEqualTo(200L);
        assertThat(cachedKey.get()).isEqualTo(300L);
        assertThat(database.loads()).isEqualTo(2);
    }

    private Cache lockingRedisCache() {
        return RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory))
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
                .build()
                .getCache("prices");
    }

    private Supplier<Long> alternating(Cache a, Cache b) {
        AtomicInteger next = new AtomicInteger();
        return () -> (next.getAndIncrement() % 2 == 0 ? a : b).get(7L, () -> database.price(7));
    }

    private void waitForLoads(int expected) throws InterruptedException {
        for (int i = 0; i < 200 && database.loads() < expected; i++) {
            Thread.sleep(10);
        }
    }
}
