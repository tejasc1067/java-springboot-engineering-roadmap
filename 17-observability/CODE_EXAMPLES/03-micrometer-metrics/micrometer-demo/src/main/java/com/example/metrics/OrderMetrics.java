package com.example.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Counting placed orders, two ways. */
public class OrderMetrics {

    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Broken: one tag value per user means one time series per user. */
    public void orderPlacedBroken(String userId) {
        Counter.builder("orders.placed").tag("userId", userId).register(registry).increment();
    }

    /** Fixed: tag by something with a handful of values. The user id belongs in the logs. */
    public void orderPlaced(String plan) {
        Counter.builder("orders.placed").tag("plan", plan).register(registry).increment();
    }
}
