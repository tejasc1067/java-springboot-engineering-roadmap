package com.example;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// One endpoint per status code, each picked the cleanest way:
//   - simple object return when 200 is the only outcome
//   - ResponseEntity when status/headers vary
//   - ResponseStatusException for "raise an error with this status"
@RestController
@RequestMapping("/api/status")
public class StatusCodesController {

    @GetMapping("/ok")
    public Map<String, String> ok() {
        return Map.of("message", "200 OK");
    }

    @GetMapping("/created")
    public ResponseEntity<Map<String, String>> created() {
        return ResponseEntity.created(URI.create("/api/status/ok"))
                .body(Map.of("message", "201 Created"));
    }

    @GetMapping("/accepted")
    public ResponseEntity<Map<String, String>> accepted() {
        return ResponseEntity.accepted().body(Map.of("jobId", "job-123"));
    }

    @GetMapping("/no-content")
    public ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bad-request")
    public ResponseEntity<Map<String, String>> badRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "malformed input"));
    }

    @GetMapping("/unauthorized")
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "missing or invalid credentials"));
    }

    @GetMapping("/forbidden")
    public ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "you are not allowed to do that"));
    }

    @GetMapping("/not-found")
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "no such resource"));
    }

    @GetMapping("/conflict")
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "duplicate key violation"));
    }

    @GetMapping("/unprocessable")
    public ResponseEntity<Map<String, String>> unprocessable() {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "title must be non-empty"));
    }

    // ResponseStatusException is the shortcut for "throw an HTTP error".
    // Useful from a service layer where you don't want to construct ResponseEntity.
    @GetMapping("/server-error")
    public Map<String, String> serverError() {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "intentional failure");
    }

    @GetMapping("/unavailable")
    public ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(Map.of("error", "service in maintenance mode"));
    }
}
