package com.example.ordersvc;

/** Raised when the inventory call fails for a non-business reason (5xx, timeout). */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
