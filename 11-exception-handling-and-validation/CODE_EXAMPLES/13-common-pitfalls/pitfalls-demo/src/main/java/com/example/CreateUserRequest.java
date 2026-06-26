package com.example;

import jakarta.validation.constraints.NotBlank;

// @NotBlank, not @NotNull: a required String must reject "" and "   ", not just null.
// @NotNull would let an empty username through (pitfall 2 in the topic).
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String email) {}
