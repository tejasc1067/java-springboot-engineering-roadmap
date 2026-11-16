package com.example.redisbasics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisContainerConfig.class)
class ExpiryTest {

    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        redis.delete(redis.keys("shop:*"));
    }

    @Test
    void setWithEx_redisDeletesTheKeyWhenTheTtlRunsOut() throws InterruptedException {
        redis.opsForValue().set("shop:otp:ann", "481516", Duration.ofMillis(200));   // SET ... PX 200

        assertThat(redis.opsForValue().get("shop:otp:ann")).isEqualTo("481516");
        Thread.sleep(400);   // a real Redis on a real clock: wait past the TTL
        assertThat(redis.opsForValue().get("shop:otp:ann")).isNull();
    }

    @Test
    void ttl_reportsSecondsLeft_minusOneForNoTtl_minusTwoForMissing() {
        redis.opsForValue().set("shop:session:abc", "ann", Duration.ofMinutes(30));
        redis.opsForValue().set("shop:config:theme", "dark");

        assertThat(redis.getExpire("shop:session:abc", TimeUnit.SECONDS)).isBetween(1_790L, 1_800L);
        assertThat(redis.getExpire("shop:config:theme")).isEqualTo(-1);     // exists, never expires
        assertThat(redis.getExpire("shop:missing")).isEqualTo(-2);          // doesn't exist

        redis.persist("shop:session:abc");                                  // PERSIST: remove the TTL
        assertThat(redis.getExpire("shop:session:abc")).isEqualTo(-1);
    }

    @Test
    void plainSet_onAKeyWithATtl_silentlyRemovesTheTtl() {
        redis.opsForValue().set("shop:cart:ann", "1 item", Duration.ofMinutes(30));

        redis.opsForValue().set("shop:cart:ann", "2 items");               // SET without EX

        assertThat(redis.getExpire("shop:cart:ann")).isEqualTo(-1);        // now it lives forever
    }

    @Test
    void incr_keepsTheExistingTtl() {
        redis.opsForValue().set("shop:ratelimit:ann", "0", Duration.ofMinutes(1));

        redis.opsForValue().increment("shop:ratelimit:ann");

        assertThat(redis.getExpire("shop:ratelimit:ann")).isPositive();    // INCR changes the value, not the TTL
    }

    @Test
    void setNx_onlyTheFirstWriterWins() {
        // SET key value NX EX 30: "set it only if it doesn't exist yet"
        Boolean first = redis.opsForValue().setIfAbsent("shop:order:1001:processing", "worker-a", Duration.ofSeconds(30));
        Boolean second = redis.opsForValue().setIfAbsent("shop:order:1001:processing", "worker-b", Duration.ofSeconds(30));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(redis.opsForValue().get("shop:order:1001:processing")).isEqualTo("worker-a");
    }
}
