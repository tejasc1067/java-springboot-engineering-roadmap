package com.example;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Everything under /api needs a logged-in caller — see SecurityConfig's anyRequest().authenticated().
// This controller has no security code of its own; the rule lives entirely in the filter chain.
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();

    public BookController() {
        store.put(1L, new Book(1L, "The Pragmatic Programmer", "Hunt & Thomas", "9780201616224"));
    }

    @GetMapping
    public Collection<Book> all() {
        return store.values();
    }

    @GetMapping("/{id}")
    public Book one(@PathVariable Long id) {
        return store.get(id);
    }
}
