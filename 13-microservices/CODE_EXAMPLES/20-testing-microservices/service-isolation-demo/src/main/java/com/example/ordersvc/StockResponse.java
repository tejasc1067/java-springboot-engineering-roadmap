package com.example.ordersvc;

/** What inventory-service returns for GET /api/inventory/{sku}: how many units are on hand. */
public record StockResponse(String sku, int available) {
}
