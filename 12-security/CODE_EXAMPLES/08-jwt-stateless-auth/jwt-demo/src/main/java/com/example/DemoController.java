package com.example;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Three routes that show the token doing its job across the authorization tiers from topic 07 — now
// driven entirely by claims inside the JWT rather than by a session.
@RestController
public class DemoController {

    // permitAll: no token needed. Proves the filter doesn't break open routes.
    @GetMapping("/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "anyone can read this");
    }

    // authenticated: needs a valid token. The Authentication here was built by JwtAuthFilter FROM the token,
    // so echoing it back proves the claims (sub, roles) survived the round trip.
    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Map.of("user", authentication.getName(), "authorities", authorities);
    }

    // hasRole('ADMIN') via the URL rule: the role that gates this comes from the token's "roles" claim,
    // not from any server-side lookup. alice's token reaches here and is refused with 403.
    @GetMapping("/api/admin/stats")
    public Map<String, Object> stats() {
        return Map.of("secret", "42");
    }
}
