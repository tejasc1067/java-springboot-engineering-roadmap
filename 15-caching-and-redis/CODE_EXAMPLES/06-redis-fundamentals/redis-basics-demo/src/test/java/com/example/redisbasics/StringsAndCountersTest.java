package com.example.redisbasics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisContainerConfig.class)
class StringsAndCountersTest {

    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        redis.delete(redis.keys("shop:*"));   // KEYS is fine on a test Redis with a handful of keys
    }

    @Test
    void string_setAndGet() {
        redis.opsForValue().set("shop:product:42:name", "Coffee mug");      // SET shop:product:42:name "Coffee mug"

        assertThat(redis.opsForValue().get("shop:product:42:name")).isEqualTo("Coffee mug");
        assertThat(redis.opsForValue().get("shop:product:99:name")).isNull();   // missing key: nil
    }

    @Test
    void readModifyWrite_inJava_losesAnUpdate() {
        redis.opsForValue().set("shop:page:home:views", "5");

        // Two app instances handle a page view at the same moment. Each reads, adds one, writes back.
        long seenByA = Long.parseLong(redis.opsForValue().get("shop:page:home:views"));
        long seenByB = Long.parseLong(redis.opsForValue().get("shop:page:home:views"));
        redis.opsForValue().set("shop:page:home:views", String.valueOf(seenByA + 1));
        redis.opsForValue().set("shop:page:home:views", String.valueOf(seenByB + 1));

        assertThat(redis.opsForValue().get("shop:page:home:views")).isEqualTo("6");   // two views, counted once
    }

    @Test
    void incr_isAtomic_noUpdateIsLost() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            IntStream.range(0, 1_000).forEach(i ->
                    pool.submit(() -> redis.opsForValue().increment("shop:page:home:views")));  // INCR
        }

        // Redis runs one command at a time, so 1,000 INCRs from 8 threads always add up to 1,000.
        assertThat(redis.opsForValue().get("shop:page:home:views")).isEqualTo("1000");
    }
}
