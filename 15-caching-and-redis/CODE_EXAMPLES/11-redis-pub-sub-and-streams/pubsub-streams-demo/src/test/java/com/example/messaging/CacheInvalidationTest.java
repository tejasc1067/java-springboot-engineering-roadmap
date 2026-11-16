package com.example.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

/**
 * Two instances, each with its own in-memory cache in front of one shared database.
 * Topic 05 showed them disagreeing after an update; here the update is broadcast and both evict.
 */
class CacheInvalidationTest extends RedisTestBase {

    private final Map<String, String> database = new ConcurrentHashMap<>(Map.of("p-1", "price=10"));

    @Test
    void updateOnInstanceA_isBroadcast_andInstanceBStopsServingTheOldPrice() throws Exception {
        try (LocalProductCache instanceA = new LocalProductCache(connectionFactory, redis);
             LocalProductCache instanceB = new LocalProductCache(connectionFactory, redis)) {
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> redis.convertAndSend(LocalProductCache.CHANNEL, "warm-up") == 2);

            assertThat(instanceA.get("p-1", database::get)).isEqualTo("price=10");
            assertThat(instanceB.get("p-1", database::get)).isEqualTo("price=10");   // both have it cached

            database.put("p-1", "price=12");      // the update request lands on instance A
            assertThat(instanceB.get("p-1", database::get)).isEqualTo("price=10");   // stale: topic 05's bug

            instanceA.evictEverywhere("p-1");

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(instanceB.get("p-1", database::get)).isEqualTo("price=12"));
            assertThat(instanceA.get("p-1", database::get)).isEqualTo("price=12");
        }
    }
}
