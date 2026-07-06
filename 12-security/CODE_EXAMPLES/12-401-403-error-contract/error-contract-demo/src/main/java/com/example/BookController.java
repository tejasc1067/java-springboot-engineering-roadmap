package com.example;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookController {

    @GetMapping("/books")
    public Map<String, Object> all() {
        return Map.of("books", new String[]{"The Pragmatic Programmer"});
    }

    // Reached only after authorization passes. Throwing here produces a 404 handled by the
    // @RestControllerAdvice — the reference shape a security 401/403 must match.
    @GetMapping("/books/{id}")
    public Map<String, Object> byId(@PathVariable long id) {
        if (id != 1) {
            throw new BookNotFoundException(id);
        }
        return Map.of("id", 1, "title", "The Pragmatic Programmer");
    }

    // Admin-only via the URL rule; a USER hitting this triggers the AccessDeniedHandler (403).
    @GetMapping("/admin/stats")
    public Map<String, Object> stats() {
        return Map.of("bookCount", 1);
    }
}
