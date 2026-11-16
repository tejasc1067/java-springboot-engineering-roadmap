package com.example.whycache;

public class PriceService {

    private final PriceDatabase database;
    private final PriceCache cache;

    public PriceService(PriceDatabase database, PriceCache cache) {
        this.database = database;
        this.cache = cache;
    }

    public long priceOf(String sku) {
        return cache.get(sku, database::findPrice);
    }

    /** Broken: the database changes, the cached copy doesn't. */
    public void changePriceWithoutEvicting(String sku, long cents) {
        database.savePrice(sku, cents);
    }

    /** Fixed: write the source of truth, then drop the copy so the next read reloads it. */
    public void changePrice(String sku, long cents) {
        database.savePrice(sku, cents);
        cache.evict(sku);
    }
}
