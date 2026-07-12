package com.example.ordersvc;

/** What order-service parses from inventory-service's GET /api/inventory/{sku} response. */
public record StockResponse(String sku, int available) {
}
