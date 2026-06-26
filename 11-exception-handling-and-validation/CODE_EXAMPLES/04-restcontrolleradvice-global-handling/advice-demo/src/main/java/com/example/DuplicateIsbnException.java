package com.example;

// Maps to 409 Conflict in GlobalExceptionHandler: the request conflicts with existing state.
public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("a book with ISBN " + isbn + " already exists");
    }
}
