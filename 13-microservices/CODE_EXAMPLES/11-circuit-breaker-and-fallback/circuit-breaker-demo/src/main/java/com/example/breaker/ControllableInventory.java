package com.example.breaker;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in inventory-service whose health is controllable: while {@code down}, every
 * call throws (an outage); when recovered, calls succeed. Counts calls so tests can
 * show that an OPEN breaker stops hitting the downstream at all.
 */
public class ControllableInventory {

    private volatile boolean down = false;
    private final AtomicInteger calls = new AtomicInteger();

    public void setDown(boolean down) {
        this.down = down;
    }

    public String getStock() {
        calls.incrementAndGet();
        if (down) {
            throw new RuntimeException("inventory-service outage");
        }
        return "{\"sku\":\"SKU-BOOK\",\"available\":5}";
    }

    public int callCount() {
        return calls.get();
    }
}
