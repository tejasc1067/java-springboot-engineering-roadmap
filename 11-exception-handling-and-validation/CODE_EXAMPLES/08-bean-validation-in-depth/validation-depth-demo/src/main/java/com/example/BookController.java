package com.example;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    // @Valid runs every constraint on the request (including the nested Publisher and the
    // tag elements) BEFORE this body executes. On any failure Spring throws
    // MethodArgumentNotValidException, which GlobalExceptionHandler turns into a 400 with fieldErrors.
    // If we reach the body, the input is valid — there is nothing to check here.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateBookRequest create(@Valid @RequestBody CreateBookRequest request) {
        return request;
    }
}
