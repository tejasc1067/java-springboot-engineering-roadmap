package com.example.logcorrelation;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String id) {
        super("order " + id + " not found");
    }
}
