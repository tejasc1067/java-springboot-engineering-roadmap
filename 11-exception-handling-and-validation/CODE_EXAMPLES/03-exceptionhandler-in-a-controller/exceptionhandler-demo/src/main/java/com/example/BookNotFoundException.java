package com.example;

// No @ResponseStatus this time. The @ExceptionHandler method owns the status and body.
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("book " + id + " not found");
    }
}
