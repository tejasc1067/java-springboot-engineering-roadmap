package com.example.retry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for a flaky inventory-service: it throws a TransientException for the
 * first {@code failuresBeforeSuccess} calls, then returns normally. Counts calls so
 * tests can assert exactly how many attempts a retry policy made.
 */
public class FlakyInventory {

    private final int failuresBeforeSuccess;
    private final AtomicInteger calls = new AtomicInteger();

    public FlakyInventory(int failuresBeforeSuccess) {
        this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    public String getStock() {
        int attempt = calls.incrementAndGet();
        if (attempt <= failuresBeforeSuccess) {
            throw new TransientException("inventory temporarily unavailable (attempt " + attempt + ")");
        }
        return "{\"sku\":\"SKU-BOOK\",\"available\":5}";
    }

    public int callCount() {
        return calls.get();
    }
}
