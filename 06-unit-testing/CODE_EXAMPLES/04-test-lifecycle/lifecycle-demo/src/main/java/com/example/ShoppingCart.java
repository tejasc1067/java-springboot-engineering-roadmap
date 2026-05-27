package com.example;

import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately stateful class: it remembers what you added.
 * Stateful objects are exactly where test isolation matters, because
 * leftover state from one test can silently change the next.
 */
public class ShoppingCart {

    private final List<String> items = new ArrayList<>();

    public void add(String sku) {
        items.add(sku);
    }

    public int size() {
        return items.size();
    }

    public boolean contains(String sku) {
        return items.contains(sku);
    }

    public void clear() {
        items.clear();
    }
}
