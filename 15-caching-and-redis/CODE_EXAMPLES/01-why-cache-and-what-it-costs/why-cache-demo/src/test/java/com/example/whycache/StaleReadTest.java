package com.example.whycache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaleReadTest {

    private final PriceDatabase database = new PriceDatabase();
    private final PriceCache cache = new PriceCache();
    private final PriceService service = new PriceService(database, cache);

    @BeforeEach
    void priceIsCached() {
        database.savePrice("sku-1", 1000);
        service.priceOf("sku-1");          // now cached at 10.00
    }

    @Test
    void updateWithoutInvalidating_usersKeepSeeingTheOldPrice() {
        service.changePriceWithoutEvicting("sku-1", 800);

        assertThat(database.findPrice("sku-1")).isEqualTo(800);
        assertThat(service.priceOf("sku-1")).isEqualTo(1000);   // stale - and stays stale, nothing expires it
    }

    @Test
    void updateThenEvict_nextReadReloadsTheNewPrice() {
        service.changePrice("sku-1", 800);

        assertThat(service.priceOf("sku-1")).isEqualTo(800);
    }

    @Test
    void aWriterThatBypassesTheService_leavesTheCacheStaleEvenWhenTheServiceEvictsCorrectly() {
        // A batch job, an admin SQL script, or another service writes the table directly.
        database.savePrice("sku-1", 700);

        assertThat(service.priceOf("sku-1")).isEqualTo(1000);
    }
}
