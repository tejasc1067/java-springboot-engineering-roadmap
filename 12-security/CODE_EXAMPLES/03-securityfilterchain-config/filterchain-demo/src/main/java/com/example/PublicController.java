package com.example;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Everything under /public is intentionally open — a health check any monitor can hit without credentials.
// The SecurityConfig marks /public/** as permitAll(); this controller is the thing that mapping points at.
@RestController
@RequestMapping("/public")
public class PublicController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
