package com.example.stampede;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class PenetrationTest extends RedisContainerSupport {

    @Autowired ProductService products;
    @Autowired SlowDatabase database;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        database.reset();
    }

    @Test
    void existingProduct_isLoadedOnce() {
        for (int i = 0; i < 100; i++) {
            products.find(42);
        }
        assertThat(database.loads()).isEqualTo(1);
    }

    @Test
    void missingProduct_isNeverCached_soEveryLookupHitsTheDatabase() {
        // A scraper or attacker walking ids that don't exist: the cache never helps.
        for (int i = 0; i < 100; i++) {
            assertThat(products.find(999_999)).isNull();
        }
        assertThat(database.loads()).isEqualTo(100);
    }

    @Test
    void negativeCaching_remembersNotFound_forAShortTime() {
        for (int i = 0; i < 100; i++) {
            assertThat(products.findWithNegativeCaching(999_999)).isNull();
        }
        products.findWithNegativeCaching(42);

        assertThat(database.loads()).isEqualTo(2);
        assertThat(redis.getExpire("productsWithNegativeCaching::999999")).isBetween(1L, 30L);
        assertThat(redis.getExpire("productsWithNegativeCaching::42")).isBetween(31L, 600L);
    }

    @Test
    void withSyncTrue_theTtlFunctionNeverSeesTheValue_soFoundProductsGetTheShortTtl() {
        // On the sync path Spring Data Redis computes the TTL before loading, passing value = null.
        products.findWithNegativeCachingSync(42);

        assertThat(redis.getExpire("productsWithNegativeCaching::42")).isBetween(1L, 30L);
    }
}
