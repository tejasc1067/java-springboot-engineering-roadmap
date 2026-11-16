package com.example.cachepatterns;

import java.util.HashMap;
import java.util.Map;

/** The source of truth. Reads and writes are counted; writes can be made to fail. */
public class Database {

    private final Map<String, Long> prices = new HashMap<>();
    private int reads;
    private int writes;
    private boolean down;

    public Long find(String sku) {
        reads++;
        return prices.get(sku);
    }

    public void save(String sku, long cents) {
        if (down) throw new IllegalStateException("database unavailable");
        writes++;
        prices.put(sku, cents);
    }

    public void goDown() {
        down = true;
    }

    public int reads() {
        return reads;
    }

    public int writes() {
        return writes;
    }
}
