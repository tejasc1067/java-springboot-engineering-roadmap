package com.example;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService service;

    public LibraryController(LibraryService service) {
        this.service = service;
    }

    // @Valid validates the request body up front: a bad shape is genuine USER input -> 400
    // (handled in the advice as MethodArgumentNotValidException). Only well-formed input
    // reaches the service, so in normal operation the service's own guards never fire.
    @PostMapping("/borrow")
    public Map<String, Object> borrow(@Valid @RequestBody BorrowRequest request) {
        int remaining = service.borrow(request.bookId(), request.copies());
        return Map.of("bookId", request.bookId(), "borrowed", request.copies(), "remaining", remaining);
    }
}
