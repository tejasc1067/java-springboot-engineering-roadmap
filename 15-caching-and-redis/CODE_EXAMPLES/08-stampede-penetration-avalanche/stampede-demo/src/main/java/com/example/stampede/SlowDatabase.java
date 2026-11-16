package com.example.stampede;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Stands in for an expensive query. Counts every load, and can be told to hold loads open
 * so a test can line up many concurrent requests on one cache miss.
 */
@Component
public class SlowDatabase {

    private final AtomicInteger loads = new AtomicInteger();
    private volatile CountDownLatch gate = new CountDownLatch(0);

    public Long price(long productId) {
        loads.incrementAndGet();
        await(gate);
        return productId * 100;
    }

    /** Returns null for ids that don't exist, like a query with no row. */
    public String product(long productId) {
        loads.incrementAndGet();
        return productId <= 1_000 ? "product-" + productId : null;
    }

    public int loads() {
        return loads.get();
    }

    public void reset() {
        loads.set(0);
        gate = new CountDownLatch(0);
    }

    /** Every load from now on blocks until the returned latch is counted down. */
    public CountDownLatch holdLoads() {
        gate = new CountDownLatch(1);
        return gate;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
