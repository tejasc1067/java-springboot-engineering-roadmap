package com.example.cachepatterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAsideTest {

    private final Database database = new Database();
    private final Cache cache = new Cache();
    private final CacheAside prices = new CacheAside(database, cache);

    @BeforeEach
    void seed() {
        database.save("sku-1", 1000);
    }

    @Test
    void firstReadMisses_andFillsTheCache_laterReadsHit() {
        prices.get("sku-1");
        prices.get("sku-1");
        prices.get("sku-1");

        assertThat(database.reads()).isEqualTo(1);
        assertThat(cache.get("sku-1")).isEqualTo(1000);
    }

    @Test
    void aWriteGoesToTheDatabaseOnly_andDeletesTheCopy_soTheNextReadReloads() {
        prices.get("sku-1");

        prices.updateThenDelete("sku-1", 800);

        assertThat(cache.get("sku-1")).isNull();          // write-around: the write didn't fill the cache
        assertThat(prices.get("sku-1")).isEqualTo(800);
    }
}
