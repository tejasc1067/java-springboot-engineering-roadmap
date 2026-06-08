package com.example;

import java.time.Instant;

// Domain object. Contains internal fields (passwordHash, createdAt) that must
// never go on the wire as-is. Module 10 will turn this into a JPA @Entity.
public class User {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Instant createdAt;

    public User(Long id, String username, String passwordHash, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
