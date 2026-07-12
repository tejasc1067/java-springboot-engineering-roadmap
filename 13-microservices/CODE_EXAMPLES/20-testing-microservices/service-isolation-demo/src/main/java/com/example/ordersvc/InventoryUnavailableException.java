package com.example.ordersvc;

/**
 * order-service depends on inventory-service to decide an order. When that call fails for a
 * reason that is not a clear business answer (a 5xx, a read timeout, a refused connection),
 * order-service must NOT guess — it raises this controlled error, which the web layer turns
 * into a 503 for its own caller. (A fallback, topic 11, would go here instead if a degraded
 * answer were acceptable.)
 */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
