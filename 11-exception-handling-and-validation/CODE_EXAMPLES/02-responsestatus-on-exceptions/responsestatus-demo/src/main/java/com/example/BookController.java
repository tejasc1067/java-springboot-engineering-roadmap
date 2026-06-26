package com.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();

    public BookController() {
        store.put(1L, new Book(1L, "The Pragmatic Programmer", "Hunt & Thomas", "9780201616224"));
    }

    @GetMapping("/{id}")
    public Book one(@PathVariable Long id) {
        Book book = store.get(id);
        if (book == null) {
            throw new BookNotFoundException(id);   // @ResponseStatus turns this into a 404
        }
        return book;
    }

    // Contrast: a plain RuntimeException with no @ResponseStatus falls through to 500.
    @GetMapping("/explode")
    public Book explode() {
        throw new IllegalStateException("nothing maps this exception, so the client sees 500");
    }
}
