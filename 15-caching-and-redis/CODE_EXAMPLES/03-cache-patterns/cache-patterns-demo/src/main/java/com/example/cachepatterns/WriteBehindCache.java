package com.example.cachepatterns;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Write-behind (write-back): writes go to the cache and return immediately; the database is updated later,
 * in batches. Until flush() runs, the only copy of the new data is in this process's memory.
 */
public class WriteBehindCache {

    private final Database database;
    private final Cache cache;
    private final Map<String, Long> pendingWrites = new LinkedHashMap<>();

    public WriteBehindCache(Database database, Cache cache) {
        this.database = database;
        this.cache = cache;
    }

    public void put(String sku, long cents) {
        cache.put(sku, cents);
        pendingWrites.put(sku, cents);   // a later write to the same key replaces the pending one
    }

    public Long get(String sku) {
        return cache.get(sku);
    }

    /** In a real system a scheduler calls this every few seconds. */
    public void flush() {
        pendingWrites.forEach(database::save);
        pendingWrites.clear();
    }

    public int pendingCount() {
        return pendingWrites.size();
    }
}
