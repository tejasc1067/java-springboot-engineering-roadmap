package com.example.cqrs;

public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String message) { super(message); }
}
