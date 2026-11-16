package com.example.schema.orderservice;

/** A compatible change: one new field, nothing renamed or removed. */
public record OrderPlacedWithCurrency(String orderId, long amountCents, String currency) { }
