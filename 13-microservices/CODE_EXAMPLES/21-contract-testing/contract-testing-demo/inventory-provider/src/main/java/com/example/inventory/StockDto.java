package com.example.inventory;

/**
 * inventory-service's response body for GET /api/inventory/{sku}. The contract pins the shape
 * of this: rename {@code available} and the generated contract test goes red before the change
 * can reach a consumer.
 */
public record StockDto(String sku, int available) {
}
