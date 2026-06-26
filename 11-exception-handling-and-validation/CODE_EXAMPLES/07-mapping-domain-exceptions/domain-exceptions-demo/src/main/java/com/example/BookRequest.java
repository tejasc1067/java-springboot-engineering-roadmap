package com.example;

// Input DTO so the JPA entity isn't exposed as a request body (module 09 topic 09).
// No validation annotations yet — that's topic 08.
public record BookRequest(String title, String author, String isbn) {
}
