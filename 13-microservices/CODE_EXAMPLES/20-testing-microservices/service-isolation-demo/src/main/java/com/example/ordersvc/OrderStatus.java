package com.example.ordersvc;

/** The three outcomes of placing an order — each decided by what inventory-service returned. */
public enum OrderStatus {
    CONFIRMED,
    REJECTED_INSUFFICIENT_STOCK,
    REJECTED_UNKNOWN_SKU
}
