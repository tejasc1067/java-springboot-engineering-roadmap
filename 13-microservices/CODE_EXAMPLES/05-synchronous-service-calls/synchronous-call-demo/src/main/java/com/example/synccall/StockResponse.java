package com.example.synccall;

/** The JSON shape the caller expects back from inventory-service. */
public record StockResponse(String sku, int available) {
}
