package com.example;

import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    // @Valid cannot name a group, so we use Spring's @Validated on the parameter.
    // Default.class pulls in title (which has no groups attribute); OnCreate.class
    // pulls in the @Null on id, so a client-supplied id is rejected here.
    @PostMapping
    public ResponseEntity<BookPayload> create(
            @Validated({Default.class, OnCreate.class}) @RequestBody BookPayload payload) {
        // Server assigns the id on create; the request must not carry one.
        BookPayload created = new BookPayload(42L, payload.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Same DTO, but OnUpdate flips id from forbidden to required.
    @PutMapping("/{id}")
    public BookPayload update(
            @PathVariable Long id,
            @Validated({Default.class, OnUpdate.class}) @RequestBody BookPayload payload) {
        return payload;
    }
}
