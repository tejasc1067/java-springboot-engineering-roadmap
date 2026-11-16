package com.example.whycache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HitRatioTest {

    private final PriceDatabase database = new PriceDatabase();

    @BeforeEach
    void thousandProducts() {
        for (int i = 0; i < 1000; i++) database.savePrice("sku-" + i, 1000 + i);
    }

    @Test
    void withoutACache_everyRequestIsADatabaseRead() {
        for (int i = 0; i < 1000; i++) database.findPrice("sku-" + (i % 10));

        assertThat(database.reads()).isEqualTo(1000);
    }

    @Test
    void popularKeys_highHitRatio_fewDatabaseReads() {
        PriceCache cache = new PriceCache();
        PriceService service = new PriceService(database, cache);

        // 1000 requests, but only 10 different products: the shape of most real traffic.
        for (int i = 0; i < 1000; i++) service.priceOf("sku-" + (i % 10));

        assertThat(database.reads()).isEqualTo(10);
        assertThat(cache.misses()).isEqualTo(10);
        assertThat(cache.hits()).isEqualTo(990);
        assertThat(cache.hitRatio()).isEqualTo(0.99);
    }

    @Test
    void keysThatNeverRepeat_zeroHitRatio_theCacheOnlyCostsMemory() {
        PriceCache cache = new PriceCache();
        PriceService service = new PriceService(database, cache);

        for (int i = 0; i < 1000; i++) service.priceOf("sku-" + i);

        assertThat(database.reads()).isEqualTo(1000);   // saved nothing
        assertThat(cache.hitRatio()).isZero();
        assertThat(cache.size()).isEqualTo(1000);       // and now holds 1000 entries nobody will read
    }

    @Test
    void coldStart_aFreshCacheSendsEveryFirstRequestToTheDatabase() {
        PriceService warm = new PriceService(database, new PriceCache());
        for (int i = 0; i < 1000; i++) warm.priceOf("sku-" + (i % 10));
        int readsBeforeRestart = database.reads();

        // A restart or deploy: the in-memory cache is empty again.
        PriceService afterRestart = new PriceService(database, new PriceCache());
        for (int i = 0; i < 10; i++) afterRestart.priceOf("sku-" + i);

        assertThat(database.reads() - readsBeforeRestart).isEqualTo(10);
    }
}
