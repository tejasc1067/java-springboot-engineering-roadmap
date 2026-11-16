package com.example.cachepatterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each test replays one interleaving of two concurrent requests, step by step.
 * The "other request" runs at a pause point, exactly where a real thread could be descheduled.
 */
class CacheAsideRaceTest {

    private final Database database = new Database();
    private final Cache cache = new Cache();
    private final CacheAside prices = new CacheAside(database, cache);

    @BeforeEach
    void priceIs1000_andNotCached() {
        database.save("sku-1", 1000);
    }

    @Test
    void deleteThenUpdate_aReadInTheGapPutsTheOldValueBack_andItStaysForever() {
        // Writer: deletes the cache entry ... (a reader arrives) ... then writes the database.
        prices.onceBetweenWriteSteps(() -> prices.get("sku-1"));   // miss, reads 1000, caches 1000

        prices.deleteThenUpdate("sku-1", 800);

        assertThat(database.find("sku-1")).isEqualTo(800);
        assertThat(cache.get("sku-1")).isEqualTo(1000);
        assertThat(prices.get("sku-1")).isEqualTo(1000);            // every later read: stale, no TTL to save it
    }

    @Test
    void updateThenSetCache_twoWritersFinishOutOfOrder_theCacheKeepsTheOlderValue() {
        // Writer A saves 900 ... (writer B saves 800 and sets the cache to 800) ... A sets the cache to 900.
        prices.onceBetweenWriteSteps(() -> prices.updateThenSetCache("sku-1", 800));

        prices.updateThenSetCache("sku-1", 900);

        assertThat(database.find("sku-1")).isEqualTo(800);          // B wrote last to the database
        assertThat(cache.get("sku-1")).isEqualTo(900);              // A wrote last to the cache
    }

    @Test
    void updateThenDelete_isStaleOnlyIfOneReadStraddlesTheWholeWrite() {
        // Reader misses and reads 1000 ... (the writer saves 800 and deletes the empty entry) ... reader caches 1000.
        prices.onceAfterDatabaseRead(() -> prices.updateThenDelete("sku-1", 800));

        Long seenByReader = prices.get("sku-1");

        assertThat(seenByReader).isEqualTo(1000);
        assertThat(database.find("sku-1")).isEqualTo(800);
        assertThat(cache.get("sku-1")).isEqualTo(1000);   // stale - but this needed the cache empty AND a
                                                          // database read slower than a whole write
    }

    @Test
    void aSecondDeleteAfterTheSlowReadHasFinished_removesTheStaleValue() {
        prices.onceAfterDatabaseRead(() -> prices.updateThenDelete("sku-1", 800));
        prices.get("sku-1");                              // the same race as above: 1000 is cached

        cache.delete("sku-1");                            // "delayed double delete": the writer deletes again later

        assertThat(prices.get("sku-1")).isEqualTo(800);
    }
}
