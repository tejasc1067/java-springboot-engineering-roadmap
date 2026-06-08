package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;

// A record DTO. Jackson finds the canonical constructor automatically and binds
// JSON keys to component names. publishedYear is renamed on the wire so the
// frontend can use snake_case while the Java stays camelCase.
public record Book(
        Long id,
        String title,
        String author,
        @JsonProperty("published_year") Integer publishedYear) {}
