package com.example;

import java.time.Instant;

// Wire shape for responses. No passwordHash. Static factory keeps the mapping
// in one place.
public record UserResponse(Long id, String username, Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getCreatedAt());
    }
}
