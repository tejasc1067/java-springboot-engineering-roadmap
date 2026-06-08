package com.example;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest req) {
        // We never reach here unless validation passed.
        return ResponseEntity.status(201).body(Map.of(
                "username", req.username(),
                "email", req.email(),
                "age", req.age()));
    }
}
