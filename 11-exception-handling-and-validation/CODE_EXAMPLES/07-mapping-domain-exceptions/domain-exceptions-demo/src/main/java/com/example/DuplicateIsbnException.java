package com.example;

public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("a book with ISBN " + isbn + " already exists");
    }
}
