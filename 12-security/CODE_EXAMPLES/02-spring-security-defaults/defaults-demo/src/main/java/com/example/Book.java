package com.example;

// The same simple domain record used across module 11. Nothing here knows or cares about security —
// that's the point of this topic: the endpoint code is unchanged; only the dependency was added.
public record Book(Long id, String title, String author, String isbn) {
}
