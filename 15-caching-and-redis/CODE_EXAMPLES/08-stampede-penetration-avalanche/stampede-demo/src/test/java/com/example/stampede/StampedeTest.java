package com.example.stampede;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class StampedeTest extends RedisContainerSupport {

    static final int REQUESTS = 20;

    @Autowired PriceService prices;
    @Autowired SlowDatabase database;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        database.reset();
    }

    @Test
    void hotKeyMissing_everyConcurrentRequestHitsTheDatabase() throws Exception {
        List<Long> results = Herd.run(REQUESTS, database, () -> prices.price(7));

        assertThat(results).containsOnly(700L);
        assertThat(database.loads()).isEqualTo(REQUESTS);
    }

    @Test
    void syncTrue_onADefaultRedisCacheManager_doesNotPreventIt() throws Exception {
        // sync = true makes Spring call cache.get(key, loader) and leaves the locking to the cache.
        // Spring Data Redis's default (non-locking) cache writer takes no lock at all.
        Herd.run(REQUESTS, database, () -> prices.priceSync(7));

        assertThat(database.loads()).isEqualTo(REQUESTS);
    }

    @Test
    void syncTrue_withALockingCacheWriter_loadsOnce() throws Exception {
        List<Long> results = Herd.run(REQUESTS, database, () -> prices.priceSyncWithLockingWriter(7));

        assertThat(results).containsOnly(700L);
        assertThat(database.loads()).isEqualTo(1);
    }
}
