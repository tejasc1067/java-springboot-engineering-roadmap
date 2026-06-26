package com.example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// title uses a built-in constraint; isbn uses our custom field constraint; loanPeriod is a nested
// object whose own class-level @ValidDateRange runs only because of @Valid here (cascading).
public record CreateBookRequest(
        @NotBlank String title,
        @ValidIsbn String isbn,
        @Valid LoanPeriod loanPeriod) {}
