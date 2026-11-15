package com.example;

// Embedded in Order: no @Document, no @Id. It only exists inside its order.
public record OrderItem(String sku, int quantity, long unitPriceCents) { }
