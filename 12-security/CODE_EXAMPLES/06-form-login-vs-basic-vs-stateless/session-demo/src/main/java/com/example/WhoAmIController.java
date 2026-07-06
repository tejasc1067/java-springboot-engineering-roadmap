package com.example;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// The same handler behind two URL spaces. /stateful/** is served by a session-based chain, /stateless/**
// by a stateless one (see SecurityConfig). The endpoint just echoes who you are so the tests can prove
// *how* you stayed authenticated between requests.
@RestController
public class WhoAmIController {

    @GetMapping({"/stateful/me", "/stateless/me"})
    public Map<String, String> me(Authentication authentication) {
        return Map.of("user", authentication.getName());
    }
}
