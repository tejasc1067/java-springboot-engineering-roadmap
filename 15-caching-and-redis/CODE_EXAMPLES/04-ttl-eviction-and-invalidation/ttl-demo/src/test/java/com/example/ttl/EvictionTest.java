package com.example.ttl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class EvictionTest {

    @Test
    void unboundedCache_growsWithEveryDistinctKey() {
        Cache<Integer, byte[]> cache = Caffeine.newBuilder().build();

        // e.g. caching search results keyed by the query text: every new query is a new key
        for (int key = 0; key < 50_000; key++) {
            cache.put(key, new byte[1_024]);
        }

        assertThat(cache.estimatedSize()).isEqualTo(50_000);   // ~50 MB, and nothing will ever remove it
    }

    @Test
    void boundedCache_staysAtItsMaximumSize() {
        Cache<Integer, byte[]> cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .executor(Runnable::run)   // evict on the calling thread so the test sees it immediately
                .build();

        for (int key = 0; key < 50_000; key++) {
            cache.put(key, new byte[1_024]);
        }
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isEqualTo(1_000);
    }

    @Test
    void pureLru_aOneOffScanPushesOutThePopularKey() {
        int maximumSize = 100;
        // LinkedHashMap in access order + removeEldestEntry is a textbook LRU cache.
        Map<String, String> lru = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maximumSize;
            }
        };

        fillWithPopularKeyThenScan(lru::put, lru::get);

        assertThat(lru.get("popular")).isNull();   // 10,000 newer keys were used after it
    }

    @Test
    void caffeine_keepsTheFrequentlyUsedKey_throughTheSameScan() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(100)
                .executor(Runnable::run)
                .build();

        fillWithPopularKeyThenScan(cache::put, cache::getIfPresent);
        cache.cleanUp();

        // W-TinyLFU also counts how often each key was used, so the scan's one-off keys
        // aren't admitted at the expense of a key read 20 times.
        assertThat(cache.getIfPresent("popular")).isEqualTo("bestseller");
    }

    private static void fillWithPopularKeyThenScan(BiConsumer<String, String> put, Function<String, String> get) {
        // A warm cache: 100 normal products, one popular product, then 50 more products.
        for (int i = 0; i < 100; i++) put.accept("product-" + i, "normal");
        put.accept("popular", "bestseller");
        for (int i = 100; i < 150; i++) put.accept("product-" + i, "normal");

        for (int i = 0; i < 20; i++) get.apply("popular");      // the popular product is read a lot

        // Then a one-off scan touches 10,000 keys exactly once each (a nightly report, a crawler).
        for (int i = 0; i < 10_000; i++) put.accept("scan-" + i, "read once");
    }
}
