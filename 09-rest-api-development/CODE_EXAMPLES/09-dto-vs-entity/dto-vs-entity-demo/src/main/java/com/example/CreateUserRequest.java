package com.example;

// Wire shape for POST. Only fields the client supplies. id, passwordHash,
// createdAt are deliberately absent -- a client cannot set them.
public record CreateUserRequest(String username, String password) {}
