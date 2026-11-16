package com.example.cachepatterns;

import java.util.HashMap;
import java.util.Map;

/** Stands in for Redis or any shared cache: get, put, delete. */
public class Cache {

    private final Map<String, Long> entries = new HashMap<>();

    public Long get(String sku) {
        return entries.get(sku);
    }

    public void put(String sku, long cents) {
        entries.put(sku, cents);
    }

    public void delete(String sku) {
        entries.remove(sku);
    }
}
