package com.example.cachepatterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadWriteThroughTest {

    private final Database database = new Database();
    private final Cache cache = new Cache();

    @BeforeEach
    void seed() {
        database.save("sku-1", 1000);
    }

    @Test
    void readThrough_theCallerOnlyAsksTheCache_theCacheLoadsOnAMiss() {
        ReadThroughCache prices = new ReadThroughCache(database::find);

        prices.get("sku-1");
        prices.get("sku-1");

        assertThat(prices.get("sku-1")).isEqualTo(1000);
        assertThat(database.reads()).isEqualTo(1);
    }

    @Test
    void writeThrough_theCacheAndTheDatabaseAgreeAfterEveryWrite() {
        WriteThroughCache prices = new WriteThroughCache(database, cache);

        prices.put("sku-1", 800);

        assertThat(database.find("sku-1")).isEqualTo(800);
        assertThat(prices.get("sku-1")).isEqualTo(800);   // already cached, no miss on the next read
    }

    @Test
    void writeThrough_whenTheDatabaseWriteFails_theCacheIsNotChangedEither() {
        WriteThroughCache prices = new WriteThroughCache(database, cache);
        prices.put("sku-1", 900);
        database.goDown();

        assertThatThrownBy(() -> prices.put("sku-1", 800)).isInstanceOf(IllegalStateException.class);

        assertThat(prices.get("sku-1")).isEqualTo(900);
    }
}
