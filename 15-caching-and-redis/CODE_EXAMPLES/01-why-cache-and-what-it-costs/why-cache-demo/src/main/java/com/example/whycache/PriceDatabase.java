package com.example.whycache;

import java.util.HashMap;
import java.util.Map;

/** Stands in for a real database. Every read is counted, because reads are what a cache saves. */
public class PriceDatabase {

    private final Map<String, Long> pricesInCents = new HashMap<>();
    private int reads;

    public long findPrice(String sku) {
        reads++;
        Long price = pricesInCents.get(sku);
        if (price == null) throw new IllegalArgumentException("no product " + sku);
        return price;
    }

    public void savePrice(String sku, long cents) {
        pricesInCents.put(sku, cents);
    }

    public int reads() {
        return reads;
    }
}
