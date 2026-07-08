package com.example.contracts;

/**
 * Same contract, but `available` is a boxed Integer. Now a missing field
 * deserializes to null instead of a misleading 0, so the consumer can DETECT that
 * the provider didn't send it (and reject/alert) rather than silently treating
 * "field gone" as "zero stock". Boxed types + explicit null checks are one defense
 * against silent contract breaks.
 */
public record StockResponseNullable(String sku, Integer available) {
}
