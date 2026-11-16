package com.example.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CacheStatsTest {

    @Autowired PriceService prices;
    @Autowired CacheManager cacheManager;
    @Autowired MeterRegistry meters;

    @Test
    void springBootPicksCaffeine_andRecordsHitsAndMisses() {
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);

        prices.updatePrice("stats-book", 1_000);
        for (int i = 0; i < 10; i++) prices.price("stats-book");   // 1 miss, then 9 hits

        @SuppressWarnings("unchecked")
        Cache<Object, Object> nativeCache =
                (Cache<Object, Object>) cacheManager.getCache("prices").getNativeCache();
        CacheStats stats = nativeCache.stats();
        assertThat(stats.missCount()).isEqualTo(1);
        assertThat(stats.hitCount()).isEqualTo(9);
        assertThat(stats.hitRate()).isEqualTo(0.9);

        // The same numbers through Micrometer: what /actuator/metrics/cache.gets and Prometheus show.
        assertThat(meters.get("cache.gets").tags("cache", "prices", "result", "hit")
                .functionCounter().count()).isEqualTo(9);
        assertThat(meters.get("cache.gets").tags("cache", "prices", "result", "miss")
                .functionCounter().count()).isEqualTo(1);
    }
}
