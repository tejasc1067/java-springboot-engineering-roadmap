package com.example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateBookRequest(

        @NotBlank
        String title,

        @NotBlank
        String author,

        // The message attribute replaces Hibernate's default ("size must be between 13 and 13").
        @Size(min = 13, max = 13, message = "ISBN must be 13 characters")
        String isbn,

        // @Valid here is what makes Publisher's own constraints run. Drop it and a blank
        // publisher name sails through — the error path that DOES appear is "publisher.name".
        @Valid @NotNull
        Publisher publisher,

        // The constraint sits on the ELEMENT type, not the list. A blank tag reports as "tags[0]".
        List<@NotBlank String> tags) {}
