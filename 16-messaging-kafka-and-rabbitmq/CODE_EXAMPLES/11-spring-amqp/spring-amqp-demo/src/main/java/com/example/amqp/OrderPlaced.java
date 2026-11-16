package com.example.amqp;

/** Not Serializable, on purpose: with JSON it doesn't need to be. */
public record OrderPlaced(String orderId, long amountCents) { }
