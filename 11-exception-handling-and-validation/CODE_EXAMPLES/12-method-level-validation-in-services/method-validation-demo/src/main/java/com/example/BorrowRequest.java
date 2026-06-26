package com.example;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

// The user-facing input contract. The controller validates THIS with @Valid (a genuine 400 on
// failure). The service's own @Min/@Positive guards are a second, defensive layer behind it.
public record BorrowRequest(
        @Min(1) Long bookId,
        @Positive int copies) {}
