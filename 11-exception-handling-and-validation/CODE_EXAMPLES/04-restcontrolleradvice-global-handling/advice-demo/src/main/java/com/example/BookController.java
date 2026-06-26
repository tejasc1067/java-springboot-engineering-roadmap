package com.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

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

    @PostMapping
    public ResponseEntity<Book> create(@RequestBody Book req) {
        boolean isbnTaken = store.values().stream().anyMatch(b -> b.isbn().equals(req.isbn()));
        if (isbnTaken) {
            throw new DuplicateIsbnException(req.isbn());
        }
        long id = seq.incrementAndGet();
        Book saved = new Book(id, req.title(), req.author(), req.isbn());
        store.put(id, saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
