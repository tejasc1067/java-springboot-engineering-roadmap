package com.example.schema.shipping;

/** shipping-service's own copy of the event: same JSON shape, different package. */
public record OrderPlaced(String orderId, long amountCents) { }
