package com.example;

import jakarta.validation.constraints.NotBlank;

// Nested object inside CreateBookRequest. Its @NotBlank only runs when the field
// that holds it is itself marked @Valid — see CreateBookRequest.publisher.
public record Publisher(@NotBlank String name) {}
