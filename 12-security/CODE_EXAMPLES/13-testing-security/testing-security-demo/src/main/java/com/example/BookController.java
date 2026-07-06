package com.example;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookController {

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of("user", authentication.getName());
    }

    // Protected by the URL rule /api/admin/** hasRole('ADMIN').
    @GetMapping("/admin/stats")
    public Map<String, Object> stats() {
        return Map.of("bookCount", 1);
    }

    // State-changing -> CSRF-protected. Tests need .with(csrf()) to reach it.
    @PostMapping("/books")
    public Map<String, String> create() {
        return Map.of("status", "created");
    }

    // Method security -> a test's @WithMockUser role decides whether @PreAuthorize passes.
    @DeleteMapping("/books/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> delete(@PathVariable long id) {
        return Map.of("deleted", id);
    }
}
