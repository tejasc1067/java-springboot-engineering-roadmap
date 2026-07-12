package com.example.ordersvc;

public record Order(String sku, int quantity, OrderStatus status) {
}
