package com.example;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "public home");
    }

    // A safe, read-only GET. CSRF protection does NOT apply to GET/HEAD/OPTIONS — they must not change state,
    // so there's nothing to forge. This is why reads work without a token.
    @GetMapping("/api/whoami")
    public Map<String, String> whoami(Authentication authentication) {
        return Map.of("user", authentication.getName());
    }

    // A state-changing POST — the classic CSRF target. With CSRF on, this requires a valid token; a forged
    // cross-site POST (which can ride the session cookie but cannot know the token) is rejected with 403.
    @PostMapping("/api/transfer")
    public Map<String, String> transfer() {
        return Map.of("status", "transferred");
    }
}
