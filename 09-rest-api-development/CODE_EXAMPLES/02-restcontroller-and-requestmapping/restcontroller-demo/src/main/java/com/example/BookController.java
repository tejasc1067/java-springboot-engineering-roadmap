package com.example;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final List<Book> SEED = List.of(
            new Book(1L, "Effective Java", "Bloch"),
            new Book(2L, "Clean Code", "Martin"),
            new Book(3L, "The Pragmatic Programmer", "Hunt and Thomas"));

    @GetMapping
    public List<Book> all() {
        return SEED;
    }

    @GetMapping("/{id}")
    public Book one(@PathVariable Long id) {
        return SEED.stream()
                .filter(b -> b.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
