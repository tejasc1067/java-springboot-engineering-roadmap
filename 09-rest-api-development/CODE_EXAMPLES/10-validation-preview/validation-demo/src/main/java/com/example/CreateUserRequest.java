package com.example;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank
        @Size(min = 3, max = 30)
        String username,

        @NotBlank
        @Email
        String email,

        @NotNull
        @Min(13)
        @Max(120)
        Integer age) {}
