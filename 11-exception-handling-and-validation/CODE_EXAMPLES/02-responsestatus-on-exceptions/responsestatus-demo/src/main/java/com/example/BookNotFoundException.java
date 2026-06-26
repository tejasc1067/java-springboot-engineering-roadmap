package com.example;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// The entire mapping is this one annotation: if this escapes a controller, the status is 404.
// No reason attribute on purpose — setting it would route through sendError and discard body control.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("book " + id + " not found");
    }
}
