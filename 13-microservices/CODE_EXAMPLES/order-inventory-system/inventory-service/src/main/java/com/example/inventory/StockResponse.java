package com.example.inventory;

/**
 * The JSON contract this service returns for a stock query. The order service has
 * its OWN copy of this shape (com.example.order.StockResponse) — the two services
 * do not share a jar of DTOs. Sharing DTO classes across services re-couples them
 * at build time and is a classic road to a distributed monolith (topics 03, 06).
 */
public record StockResponse(String sku, int available) {
}
