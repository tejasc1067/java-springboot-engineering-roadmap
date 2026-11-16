package com.example.rediscache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;

/** No cache configuration at all: just spring-boot-starter-data-redis + @EnableCaching. */
@SpringBootTest
class DefaultJdkSerializationTest extends RedisContainerSupport {

    @Autowired ProductService service;
    @Autowired CacheManager cacheManager;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void emptyRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void defaultSerializer_failsOnAClassThatIsNotSerializable() {
        assertThatThrownBy(() -> service.find(1))
                .isInstanceOf(SerializationException.class)
                .rootCause()
                .hasMessageContaining("DefaultSerializer requires a Serializable payload");
    }

    record SerializableProduct(long id, String name) implements Serializable { }

    @Test
    void serializableWorks_butStoresJavaBytes_andNeverExpires() {
        cacheManager.getCache("products").put(1L, new SerializableProduct(1, "Keyboard"));

        byte[] stored = redis.getConnectionFactory().getConnection().stringCommands()
                .get("products::1".getBytes(StandardCharsets.UTF_8));

        // 0xACED is the Java serialization magic number: only a JVM with this exact class can read it.
        assertThat(stored[0]).isEqualTo((byte) 0xAC);
        assertThat(stored[1]).isEqualTo((byte) 0xED);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1))
                .contains("com.example.rediscache.DefaultJdkSerializationTest$SerializableProduct");

        // -1 means "no expiry": with Boot's defaults, cache entries live until someone deletes them.
        assertThat(redis.getExpire("products::1")).isEqualTo(-1L);
    }
}
