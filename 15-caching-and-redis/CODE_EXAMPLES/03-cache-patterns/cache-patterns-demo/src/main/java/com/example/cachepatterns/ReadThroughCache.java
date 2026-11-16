package com.example.cachepatterns;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Read-through: callers only talk to the cache; on a miss the cache itself loads from the source. */
public class ReadThroughCache {

    private final Map<String, Long> entries = new HashMap<>();
    private final Function<String, Long> loader;

    public ReadThroughCache(Function<String, Long> loader) {
        this.loader = loader;
    }

    public Long get(String sku) {
        return entries.computeIfAbsent(sku, loader);
    }
}
