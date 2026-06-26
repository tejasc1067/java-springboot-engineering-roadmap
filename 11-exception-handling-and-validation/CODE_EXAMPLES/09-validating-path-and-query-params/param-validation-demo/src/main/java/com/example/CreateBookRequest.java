package com.example;

import jakarta.validation.constraints.NotBlank;

// Request body for POST. @Valid @RequestBody failures throw MethodArgumentNotValidException.
public record CreateBookRequest(@NotBlank String title) {}
