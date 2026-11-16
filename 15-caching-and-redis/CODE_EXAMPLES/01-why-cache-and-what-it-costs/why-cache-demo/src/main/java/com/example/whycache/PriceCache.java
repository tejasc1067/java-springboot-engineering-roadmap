package com.example.whycache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The smallest useful cache: a map plus hit/miss counters.
 * ponytail: unbounded and never expires - topic 04 adds size limits and TTLs.
 */
public class PriceCache {

    private final Map<String, Long> entries = new HashMap<>();
    private int hits;
    private int misses;

    public long get(String sku, Function<String, Long> loadFromSource) {
        Long cached = entries.get(sku);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        long loaded = loadFromSource.apply(sku);
        entries.put(sku, loaded);
        return loaded;
    }

    public void evict(String sku) {
        entries.remove(sku);
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    public double hitRatio() {
        int total = hits + misses;
        return total == 0 ? 0 : (double) hits / total;
    }

    public int size() {
        return entries.size();
    }
}
