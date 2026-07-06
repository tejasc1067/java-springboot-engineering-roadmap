package com.example;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookController {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();

    public BookController() {
        store.put(1L, new Book(1L, "The Pragmatic Programmer", "Hunt & Thomas", "9780201616224"));
    }

    // URL rule is anyRequest().authenticated(): ANY logged-in user (USER or ADMIN) may read.
    @GetMapping("/books")
    public Collection<Book> all() {
        return store.values();
    }

    // METHOD-level rule. The URL rule for /api/books/** only requires "authenticated", but @PreAuthorize
    // adds "must be ADMIN" to this one method. alice (ROLE_USER) is authenticated yet forbidden here -> 403.
    @DeleteMapping("/books/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        store.remove(id);
    }

    // URL-based admin area: /api/admin/** is gated by hasRole('ADMIN') in SecurityConfig, so this method
    // needs no annotation of its own — the path rule already protects it.
    @GetMapping("/admin/stats")
    public Map<String, Object> stats() {
        return Map.of("bookCount", store.size());
    }

    // EXPRESSION-based ownership: you may see your OWN profile, OR you're an admin. #username binds the
    // path variable (parameter names are retained because Spring Boot compiles with -parameters);
    // authentication.name is the logged-in user.
    @GetMapping("/users/{username}/profile")
    @PreAuthorize("hasRole('ADMIN') or #username == authentication.name")
    public Map<String, String> profile(@PathVariable String username) {
        return Map.of("profileOf", username);
    }
}
