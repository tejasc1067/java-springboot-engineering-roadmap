package com.example.redisbasics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisContainerConfig.class)
class ScanTest {

    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void fill() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        Map<String, String> keys = new HashMap<>();
        for (int i = 0; i < 5_000; i++) keys.put("shop:product:" + i, "p" + i);
        for (int i = 0; i < 20; i++) keys.put("shop:session:" + i, "s" + i);
        redis.opsForValue().multiSet(keys);                                   // MSET, one round trip
    }

    @Test
    void scan_walksTheKeysInSmallBatches() {
        Set<String> sessions = new HashSet<>();
        // SCAN 0 MATCH shop:session:* COUNT 100, repeated with each returned cursor until it's 0 again.
        // Each call does a little work, so other clients' commands run in between.
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match("shop:session:*").count(100).build())) {
            cursor.forEachRemaining(sessions::add);
        }

        assertThat(sessions).hasSize(20);
    }

    @Test
    void keys_returnsTheSameAnswer_butWalksEveryKeyInOneBlockingCommand() {
        // KEYS shop:session:* checks all 5,020 keys before replying, and Redis runs nothing else
        // meanwhile. With 50 million keys in production, that's every client stalled for seconds.
        assertThat(redis.keys("shop:session:*")).hasSize(20);
    }
}
