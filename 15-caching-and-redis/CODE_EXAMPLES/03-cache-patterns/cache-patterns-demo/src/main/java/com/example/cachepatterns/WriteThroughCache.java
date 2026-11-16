package com.example.cachepatterns;

/** Write-through: every write goes to the database and the cache, synchronously, before returning. */
public class WriteThroughCache {

    private final Database database;
    private final Cache cache;

    public WriteThroughCache(Database database, Cache cache) {
        this.database = database;
        this.cache = cache;
    }

    public void put(String sku, long cents) {
        database.save(sku, cents);   // if this throws, the cache is never touched
        cache.put(sku, cents);
    }

    public Long get(String sku) {
        return cache.get(sku);
    }
}
