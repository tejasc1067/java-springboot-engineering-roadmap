package com.example;

/** An order's state. Only NEW and PAID orders can still be cancelled. */
public enum OrderStatus {
    NEW(true),
    PAID(true),
    SHIPPED(false),
    CANCELLED(false);

    private final boolean cancellable;

    OrderStatus(boolean cancellable) {
        this.cancellable = cancellable;
    }

    public boolean canBeCancelled() {
        return cancellable;
    }
}
