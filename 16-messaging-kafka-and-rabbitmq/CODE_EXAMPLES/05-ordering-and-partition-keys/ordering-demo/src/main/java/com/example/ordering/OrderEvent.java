package com.example.ordering;

/** Sent as a plain string "orderId:TYPE", e.g. "order-7:PAID", so the demo needs no JSON setup. */
public record OrderEvent(String orderId, Type type) {

    public enum Type { CREATED, PAID, SHIPPED }

    public static OrderEvent parse(String value) {
        String[] parts = value.split(":");
        return new OrderEvent(parts[0], Type.valueOf(parts[1]));
    }

    public String value() {
        return orderId + ":" + type;
    }
}
