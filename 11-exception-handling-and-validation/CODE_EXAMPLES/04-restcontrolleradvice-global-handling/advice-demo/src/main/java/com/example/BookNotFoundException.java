package com.example;

// Plain RuntimeException. The status (404) lives in GlobalExceptionHandler, not here.
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("book " + id + " not found");
    }
}
