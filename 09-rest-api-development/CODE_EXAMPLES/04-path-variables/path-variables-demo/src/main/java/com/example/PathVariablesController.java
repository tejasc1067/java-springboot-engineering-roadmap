package com.example;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PathVariablesController {

    // Single Long path variable. "abc" -> 400 (conversion fails).
    @GetMapping("/books/{id}")
    public Map<String, Object> book(@PathVariable Long id) {
        return Map.of("id", id, "title", "Effective Java");
    }

    // Two path variables; binding is by name, not position.
    @GetMapping("/books/{bookId}/reviews/{reviewId}")
    public Map<String, Object> review(@PathVariable Long bookId,
                                      @PathVariable Long reviewId) {
        return Map.of("bookId", bookId, "reviewId", reviewId);
    }

    // Regex constraint: only lowercase, digits, underscore.
    // "Alice" -> 404 (the URL does not match this handler at all).
    @GetMapping("/users/{username:[a-z0-9_]+}")
    public Map<String, String> user(@PathVariable String username) {
        return Map.of("username", username);
    }

    // LocalDate is converted from ISO-8601 (yyyy-MM-dd) out of the box.
    // "not-a-date" -> 400.
    @GetMapping("/events/by-date/{date}")
    public Map<String, Object> events(@PathVariable LocalDate date) {
        return Map.of("date", date.toString(), "count", 0);
    }
}
