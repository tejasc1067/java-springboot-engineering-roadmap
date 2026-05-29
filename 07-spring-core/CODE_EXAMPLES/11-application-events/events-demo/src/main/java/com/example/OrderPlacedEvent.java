package com.example;

// Plain record. No Spring base class to extend; modern Spring works with any POJO.
public record OrderPlacedEvent(String orderId, String customer) {
}
