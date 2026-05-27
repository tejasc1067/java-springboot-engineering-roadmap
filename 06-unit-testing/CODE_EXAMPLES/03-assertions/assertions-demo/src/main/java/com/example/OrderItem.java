package com.example;

/**
 * One line on an order. A record gives us equals/hashCode/toString for free,
 * which matters in tests: assertEquals on two records compares their fields.
 */
public record OrderItem(String sku, int quantity, long unitPriceCents) {

    public OrderItem {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("unitPriceCents must not be negative");
        }
    }

    public long lineTotalCents() {
        return unitPriceCents * quantity;
    }
}
