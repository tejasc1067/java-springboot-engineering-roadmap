package com.example.schema.orderservice;

/** The event as order-service's code defines it. Shipping-service never sees this class. */
public record OrderPlaced(String orderId, long amountCents) { }
