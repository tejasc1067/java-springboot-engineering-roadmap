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
            throw new BookNotFoundException(id);
        }
        return book;
    }

    // Simulates a deep failure whose exception message carries internal secrets. The contract's
    // job is to make sure that message reaches the LOG but never the client.
    @GetMapping("/leak")
    public Book leak() {
        throw new IllegalStateException(
                "connection failed: jdbc:postgresql://prod-db:5432/books password=hunter2");
    }
}
