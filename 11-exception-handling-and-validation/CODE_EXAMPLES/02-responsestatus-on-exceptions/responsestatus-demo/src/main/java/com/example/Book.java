package com.example;

// The domain record reused across module 11. id is null until the store assigns one.
public record Book(Long id, String title, String author, String isbn) {
}
