package com.example;

// A checked exception (extends Exception, not RuntimeException).
public class OutOfPagesException extends Exception {
    public OutOfPagesException(String message) { super(message); }
}
