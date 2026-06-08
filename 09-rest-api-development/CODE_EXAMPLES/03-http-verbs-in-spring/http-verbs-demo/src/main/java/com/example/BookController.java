package com.example;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    // The "database" is in-memory. Real persistence arrives in module 10.
    private final Map<Long, Book> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @GetMapping
    public Collection<Book> all() {
        return store.values();
    }

    @GetMapping("/{id}")
    public Book one(@PathVariable Long id) {
        return store.get(id);
    }

    @PostMapping
    public Book create(@RequestBody Book incoming) {
        long id = nextId.getAndIncrement();
        Book created = new Book(id, incoming.title(), incoming.author());
        store.put(id, created);
        return created;
    }

    // PUT replaces the whole record. Fields omitted from the body become null.
    @PutMapping("/{id}")
    public Book replace(@PathVariable Long id, @RequestBody Book incoming) {
        Book replacement = new Book(id, incoming.title(), incoming.author());
        store.put(id, replacement);
        return replacement;
    }

    // PATCH only touches fields present in the body; everything else is preserved.
    // Map<String, Object> is the simplest receiver — real APIs usually define a
    // dedicated PatchBookRequest DTO. Topic 09 covers DTOs.
    @PatchMapping("/{id}")
    public Book patch(@PathVariable Long id, @RequestBody Map<String, Object> changes) {
        Book current = store.get(id);
        String title  = (String) changes.getOrDefault("title", current.title());
        String author = (String) changes.getOrDefault("author", current.author());
        Book updated = new Book(id, title, author);
        store.put(id, updated);
        return updated;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        store.remove(id);
    }
}
