package com.example;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    // Whatever JSON the client sends, Jackson turns it into a Book and we echo
    // it back. No validation yet -- topic 10 adds @Valid.
    @PostMapping
    public Book create(@RequestBody Book incoming) {
        return new Book(99L, incoming.title(), incoming.author(), incoming.publishedYear());
    }
}
