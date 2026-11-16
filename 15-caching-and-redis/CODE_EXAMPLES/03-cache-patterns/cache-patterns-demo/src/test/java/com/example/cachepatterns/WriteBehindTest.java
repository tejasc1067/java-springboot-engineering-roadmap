package com.example.cachepatterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WriteBehindTest {

    private final Database database = new Database();
    private final Cache cache = new Cache();

    @BeforeEach
    void seed() {
        database.save("sku-1", 1000);
    }

    @Test
    void writesReturnWithoutTouchingTheDatabase_andRepeatedWritesCoalesceIntoOne() {
        WriteBehindCache prices = new WriteBehindCache(database, cache);
        int writesBefore = database.writes();

        for (long cents = 901; cents <= 910; cents++) prices.put("sku-1", cents);

        assertThat(database.writes()).isEqualTo(writesBefore);        // 10 writes, 0 database writes so far
        prices.flush();
        assertThat(database.writes()).isEqualTo(writesBefore + 1);    // ... and 1 after the flush
        assertThat(database.find("sku-1")).isEqualTo(910);
    }

    @Test
    void untilTheFlush_anyoneReadingTheDatabaseSeesTheOldValue() {
        WriteBehindCache prices = new WriteBehindCache(database, cache);

        prices.put("sku-1", 800);

        assertThat(prices.get("sku-1")).isEqualTo(800);
        assertThat(database.find("sku-1")).isEqualTo(1000);   // a report, another service, a SQL console
    }

    @Test
    void theProcessDiesBeforeTheFlush_theWritesAreGone() {
        WriteBehindCache prices = new WriteBehindCache(database, cache);
        prices.put("sku-1", 800);
        prices.put("sku-2", 450);
        assertThat(prices.pendingCount()).isEqualTo(2);

        // Crash here: the JVM is killed (OOM, deploy, kill -9) and flush() never runs.
        // The pending writes lived only in its memory.

        assertThat(database.find("sku-1")).isEqualTo(1000);
        assertThat(database.find("sku-2")).isNull();
    }
}
