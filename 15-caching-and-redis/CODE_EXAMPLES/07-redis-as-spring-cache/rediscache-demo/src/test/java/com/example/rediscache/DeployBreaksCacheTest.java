package com.example.rediscache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Each test plants an entry that the PREVIOUS version of the app wrote, then runs the NEW code against it.
 * Redis outlives deploys, so this is exactly what the first requests after a release see.
 */
class DeployBreaksCacheTest {

    static final String RENAMED_CLASS =
            "{\"@class\":\"com.example.rediscache.CatalogItem\",\"id\":1,\"name\":\"Keyboard\",\"priceCents\":4999}";
    static final String REMOVED_FIELD =
            "{\"@class\":\"com.example.rediscache.Product\",\"id\":1,\"name\":\"Keyboard\",\"priceCents\":4999,\"currency\":\"EUR\"}";

    @Nested
    @SpringBootTest
    @ActiveProfiles("json")
    class SameKeyPrefix extends RedisContainerSupport {

        @Autowired ProductService service;
        @Autowired StringRedisTemplate redis;

        @BeforeEach
        void emptyRedis() {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        }

        @Test
        void entryWrittenByARenamedClass_failsEveryRead() {
            redis.opsForValue().set("v1:products::1", RENAMED_CLASS);

            assertThatThrownBy(() -> service.find(1))
                    .isInstanceOf(SerializationException.class)
                    .hasMessageContaining("Could not read JSON");
        }

        @Test
        void entryWithAFieldTheNewClassNoLongerHas_failsEveryRead() {
            redis.opsForValue().set("v1:products::1", REMOVED_FIELD);

            assertThatThrownBy(() -> service.find(1))
                    .isInstanceOf(SerializationException.class)
                    .hasMessageContaining("Unrecognized field \"currency\"");
        }
    }

    @Nested
    @SpringBootTest(properties = "app.cache.version=v2")
    @ActiveProfiles("json")
    class BumpedKeyPrefix extends RedisContainerSupport {

        @Autowired ProductService service;
        @Autowired ProductCatalog catalog;
        @Autowired StringRedisTemplate redis;

        @Test
        void newVersionUsesNewKeys_andNeverReadsTheOldEntries() {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
            redis.opsForValue().set("v1:products::1", RENAMED_CLASS);
            int before = catalog.reads();

            assertThat(service.find(1).name()).isEqualTo("Keyboard");

            assertThat(catalog.reads() - before).isEqualTo(1);
            assertThat(redis.keys("*")).containsExactlyInAnyOrder("v1:products::1", "v2:products::1");
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("json")
    @Import(BadEntryIsAMiss.TolerantCaching.class)
    class BadEntryIsAMiss extends RedisContainerSupport {

        // A safety net, not a replacement for versioned keys: a cache read that fails is logged and treated as a miss.
        @TestConfiguration
        static class TolerantCaching implements CachingConfigurer {
            @Override
            public CacheErrorHandler errorHandler() {
                return new LoggingCacheErrorHandler();
            }
        }

        @Autowired ProductService service;
        @Autowired StringRedisTemplate redis;

        @Test
        void unreadableEntry_fallsBackToTheDatabase() {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
            redis.opsForValue().set("v1:products::1", RENAMED_CLASS);

            assertThat(service.find(1).name()).isEqualTo("Keyboard");
        }
    }
}
