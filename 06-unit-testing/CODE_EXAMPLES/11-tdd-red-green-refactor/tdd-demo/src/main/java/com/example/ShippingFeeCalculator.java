package com.example;

/**
 * Built test-first in topic 11. This is the final state after several
 * red-green-refactor cycles; the markdown walks through how it got here one test
 * at a time. Rules:
 *   - orders at or above the free-shipping threshold ship for free
 *   - smaller orders pay a flat fee
 *   - a negative total is rejected
 */
public class ShippingFeeCalculator {

    private static final long FREE_SHIPPING_THRESHOLD_CENTS = 5000;
    private static final long FLAT_FEE_CENTS = 599;

    public long feeFor(long orderTotalCents) {
        if (orderTotalCents < 0) {
            throw new IllegalArgumentException("orderTotalCents must not be negative");
        }
        return orderTotalCents >= FREE_SHIPPING_THRESHOLD_CENTS ? 0 : FLAT_FEE_CENTS;
    }
}
