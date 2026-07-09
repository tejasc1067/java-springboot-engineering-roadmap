package com.example.asyncevents;

/**
 * An EVENT: a statement that something already happened, in the past tense.
 * order-service publishes this after an order is placed; any number of other
 * services (notifications, shipping, analytics) can react to it without
 * order-service knowing they exist. Contrast a COMMAND ("ReserveStock"), which
 * tells one specific service to do something.
 */
public record OrderPlacedEvent(Long orderId, String sku, int quantity) {
}
