package com.example.ttl;

import java.util.HashMap;
import java.util.Map;

/** Stands in for a real database: holds the true prices and counts how often it's queried. */
public class PriceDatabase {

    private final Map<String, Long> prices = new HashMap<>();
    private int queries;

    public long load(String sku) {
        queries++;
        return prices.getOrDefault(sku, 0L);
    }

    public void update(String sku, long cents) {
        prices.put(sku, cents);
    }

    public int queries() {
        return queries;
    }
}
