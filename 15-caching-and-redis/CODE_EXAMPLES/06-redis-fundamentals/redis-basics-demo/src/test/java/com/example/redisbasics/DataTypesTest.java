package com.example.redisbasics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisContainerConfig.class)
class DataTypesTest {

    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        redis.delete(redis.keys("shop:*"));
    }

    @Test
    void hash_oneKeyHoldsTheFieldsOfAnObject() {
        // HSET shop:user:7 name Ann city Pune visits 0
        redis.opsForHash().putAll("shop:user:7", Map.of("name", "Ann", "city", "Pune", "visits", "0"));

        redis.opsForHash().put("shop:user:7", "city", "Mumbai");          // change one field, not the whole object
        redis.opsForHash().increment("shop:user:7", "visits", 1);         // HINCRBY, atomic like INCR

        assertThat(redis.opsForHash().get("shop:user:7", "city")).isEqualTo("Mumbai");
        assertThat(redis.<String, String>opsForHash().entries("shop:user:7"))
                .containsEntry("name", "Ann")
                .containsEntry("visits", "1");
    }

    @Test
    void list_recentlyViewed_keepsTheNewestThree() {
        for (String product : List.of("mug", "pen", "book", "lamp", "desk")) {
            redis.opsForList().leftPush("shop:user:7:recent", product);    // LPUSH: newest at the front
            redis.opsForList().trim("shop:user:7:recent", 0, 2);           // LTRIM: keep only 3
        }

        assertThat(redis.opsForList().range("shop:user:7:recent", 0, -1)).containsExactly("desk", "lamp", "book");
    }

    @Test
    void set_uniqueVisitors_ignoresDuplicates() {
        redis.opsForSet().add("shop:visitors:2026-11-16", "ann", "bob", "ann", "cid", "bob");  // SADD

        assertThat(redis.opsForSet().size("shop:visitors:2026-11-16")).isEqualTo(3);           // SCARD
        assertThat(redis.opsForSet().isMember("shop:visitors:2026-11-16", "bob")).isTrue();      // SISMEMBER
    }

    @Test
    void sortedSet_leaderboard_staysOrderedByScore() {
        redis.opsForZSet().incrementScore("shop:leaderboard", "ann", 50);   // ZINCRBY
        redis.opsForZSet().incrementScore("shop:leaderboard", "bob", 80);
        redis.opsForZSet().incrementScore("shop:leaderboard", "cid", 65);
        redis.opsForZSet().incrementScore("shop:leaderboard", "ann", 40);   // ann now 90

        // ZREVRANGE shop:leaderboard 0 1 WITHSCORES: top two, highest first
        Set<TypedTuple<String>> top2 = redis.opsForZSet().reverseRangeWithScores("shop:leaderboard", 0, 1);

        assertThat(top2).extracting(TypedTuple::getValue).containsExactly("ann", "bob");
        assertThat(top2).extracting(TypedTuple::getScore).containsExactly(90.0, 80.0);
        assertThat(redis.opsForZSet().reverseRank("shop:leaderboard", "cid")).isEqualTo(2);  // 0-based: third place
    }
}
