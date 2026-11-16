package com.example.ttl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidationTest {

    private final PriceDatabase db = new PriceDatabase();
    private final LoadingCache<String, Long> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .build(db::load);

    /** The broken write path: updates the database and forgets the cache. */
    private void updatePriceBroken(String sku, long cents) {
        db.update(sku, cents);
    }

    /** The fixed write path: update the database first, then delete the cached copy. */
    private void updatePrice(String sku, long cents) {
        db.update(sku, cents);
        cache.invalidate(sku);
    }

    @Test
    void withoutInvalidation_readersSeeTheOldPriceForTheWholeTtl() {
        db.update("book", 1_000);
        cache.get("book");

        updatePriceBroken("book", 1_500);

        assertThat(cache.get("book")).isEqualTo(1_000);    // and for up to an hour
    }

    @Test
    void invalidateOnWrite_nextReadLoadsTheNewPrice() {
        db.update("book", 1_000);
        cache.get("book");

        updatePrice("book", 1_500);

        assertThat(cache.get("book")).isEqualTo(1_500);
        assertThat(db.queries()).isEqualTo(2);
    }
}
