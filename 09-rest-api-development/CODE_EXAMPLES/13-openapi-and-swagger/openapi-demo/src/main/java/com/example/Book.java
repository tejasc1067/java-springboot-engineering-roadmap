package com.example;

import io.swagger.v3.oas.annotations.media.Schema;

public record Book(

        @Schema(description = "Server-assigned identifier", example = "42")
        Long id,

        @Schema(description = "Book title", example = "Effective Java")
        String title,

        @Schema(description = "Author full name", example = "Joshua Bloch")
        String author) {}
