package com.example;

/**
 * Pure pricing logic: no database, no network, no clock.
 * Every method depends only on its arguments, which is what makes it trivial to unit-test.
 * Money is kept in whole cents (long) so we never compare floating-point values in a test.
 */
public class PriceCalculator {

    /** Cost of one order line, in cents. */
    public long lineTotal(long unitPriceCents, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return unitPriceCents * quantity;
    }
}
