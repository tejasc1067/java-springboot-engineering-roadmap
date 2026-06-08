package com.example;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QueryParametersController {

    private static final List<Book> CATALOG = List.of(
            new Book(1L, "Effective Java", "Bloch", List.of("java", "best-practices")),
            new Book(2L, "Java Concurrency in Practice", "Goetz", List.of("java", "concurrency")),
            new Book(3L, "Spring in Action", "Walls", List.of("java", "spring")),
            new Book(4L, "Clean Code", "Martin", List.of("clean-code")),
            new Book(5L, "The Pragmatic Programmer", "Hunt", List.of("general")));

    // Defaults: optional with sensible fallbacks (paging pattern).
    @GetMapping("/books")
    public List<Book> list(@RequestParam(defaultValue = "0")  int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false)    String author,
                           @RequestParam(required = false)    List<String> tag) {

        return CATALOG.stream()
                .filter(b -> author == null || b.author().equals(author))
                .filter(b -> tag == null || tag.isEmpty() || b.tags().containsAll(tag))
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    // Required parameter -> 400 if absent.
    @GetMapping("/search")
    public List<Book> search(@RequestParam String author) {
        return CATALOG.stream()
                .filter(b -> b.author().equals(author))
                .toList();
    }

    // Catch-all map. Useful for admin/debug endpoints, rarely for the real API.
    @GetMapping("/debug")
    public Map<String, String> debug(@RequestParam Map<String, String> all) {
        return all;
    }
}
