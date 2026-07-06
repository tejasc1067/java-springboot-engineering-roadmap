package com.example;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A completely ordinary, security-unaware controller — no annotations about who may call it, no login
// logic, nothing. In an app without spring-boot-starter-security on the classpath, every method here is
// open to the world. Add the starter (see the pom) and Spring Security locks all of it, without one line
// of change in this file. That gap — open by absence, locked by presence — is the whole lesson.
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
