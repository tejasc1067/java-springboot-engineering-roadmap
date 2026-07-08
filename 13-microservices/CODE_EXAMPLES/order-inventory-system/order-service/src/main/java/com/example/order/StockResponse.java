package com.example.order;

/**
 * order-service's OWN copy of the shape it expects back from inventory-service.
 * It is a separate class from inventory's StockResponse on purpose: the two
 * services agree on a JSON contract (fields "sku" and "available"), not on a
 * shared Java class. Either side can be redeployed without recompiling the other.
 * Being a "tolerant reader" — ignoring any extra fields inventory adds later —
 * is what lets inventory evolve its API without breaking us (topic 06).
 */
public record StockResponse(String sku, int available) {
}
