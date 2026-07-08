package com.example.contracts;

/**
 * The CONSUMER's model of inventory's response — order-service's view of the
 * contract. It cares about two fields: sku and available. Note `available` is a
 * primitive int: if the provider stops sending that field (e.g. renames it), this
 * deserializes to 0 with NO error — a silent break. See StockResponseNullable for
 * the safer boxed-type version that makes a missing field detectable.
 */
public record StockResponse(String sku, int available) {
}
