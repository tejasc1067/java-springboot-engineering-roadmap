package com.example;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The protected resource. Every request here requires an authenticated user — one that now comes from the
// database via JpaUserDetailsService, not from a generated password.
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
}
