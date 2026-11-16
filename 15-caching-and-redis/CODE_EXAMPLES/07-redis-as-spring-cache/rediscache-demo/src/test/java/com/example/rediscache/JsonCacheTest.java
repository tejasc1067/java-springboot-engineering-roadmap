package com.example.rediscache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("json")
class JsonCacheTest extends RedisContainerSupport {

    @Autowired ProductService service;
    @Autowired ProductCatalog catalog;
    @Autowired CacheManager cacheManager;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void emptyRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void secondCall_isServedFromRedis() {
        int before = catalog.reads();

        service.find(1);
        service.find(1);

        assertThat(catalog.reads() - before).isEqualTo(1);
    }

    @Test
    void storedValue_isReadableJson_withAVersionedKey_andATtl() {
        service.find(1);

        assertThat(redis.keys("*")).containsExactly("v1:products::1");
        assertThat(redis.opsForValue().get("v1:products::1"))
                .isEqualTo("{\"@class\":\"com.example.rediscache.Product\",\"id\":1,\"name\":\"Keyboard\",\"priceCents\":4999}");
        assertThat(redis.getExpire("v1:products::1")).isBetween(1L, 600L);
    }

    @Test
    void cachingANull_isAnErrorWhenNullsAreDisabled() {
        assertThatThrownBy(() -> cacheManager.getCache("products").put(99L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not allow 'null' values");
    }

    @Test
    void withUnless_aMissingProductIsSimplyNotCached() {
        assertThat(service.find(99)).isNull();
        assertThat(redis.keys("*")).isEmpty();
    }
}
