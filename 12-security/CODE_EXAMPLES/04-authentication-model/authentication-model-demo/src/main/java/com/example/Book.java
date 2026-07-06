package com.example;

// The protected resource — a plain record, not a JPA entity. Only AppUser lives in the database here.
public record Book(Long id, String title, String author, String isbn) {
}
