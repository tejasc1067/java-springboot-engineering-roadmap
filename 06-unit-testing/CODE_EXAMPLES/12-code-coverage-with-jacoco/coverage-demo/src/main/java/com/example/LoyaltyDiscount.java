package com.example;

/**
 * Discount percent by loyalty tier. It has four branches:
 *   GOLD + large order, GOLD + small order, SILVER, and "anything else".
 * The tests deliberately cover only three of them, so the JaCoCo report shows
 * a gap on the SILVER branch - that gap is the lesson in topic 12.
 */
public class LoyaltyDiscount {

    public int percentFor(String tier, long orderCents) {
        if ("GOLD".equals(tier)) {
            return orderCents >= 10000 ? 20 : 15;
        }
        if ("SILVER".equals(tier)) {
            return 10;
        }
        return 0;   // unknown or no tier
    }
}
