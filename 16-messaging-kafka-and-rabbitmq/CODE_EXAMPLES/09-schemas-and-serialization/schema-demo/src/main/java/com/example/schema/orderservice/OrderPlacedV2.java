package com.example.schema.orderservice;

/** order-service's next release renamed amountCents to totalCents. Same topic, same consumers. */
public record OrderPlacedV2(String orderId, long totalCents) { }
