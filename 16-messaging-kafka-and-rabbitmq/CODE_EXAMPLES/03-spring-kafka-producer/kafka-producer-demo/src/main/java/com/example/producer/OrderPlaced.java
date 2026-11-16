package com.example.producer;

public record OrderPlaced(String orderId, String customerId, long totalCents) {
}
