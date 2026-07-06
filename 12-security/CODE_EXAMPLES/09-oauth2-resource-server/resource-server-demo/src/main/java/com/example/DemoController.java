package com.example;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Routes that show a validated token driving access. The identity here is a Jwt: after oauth2ResourceServer
// verifies the token, the principal IS the decoded token, so you can read its claims directly.
@RestController
public class DemoController {

    @GetMapping("/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "anyone can read this");
    }

    // Any valid token. @AuthenticationPrincipal Jwt gives you the decoded, verified token — proof that the
    // claims the external IdP signed survived validation. Note we read "sub" and "scope" straight off it.
    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "subject", jwt.getSubject(),
                "issuer", String.valueOf(jwt.getIssuer()),
                "scopes", jwt.getClaimAsString("scope"));
    }

    // Requires SCOPE_books:read (enforced by the URL rule in SecurityConfig).
    @GetMapping("/api/books")
    public List<Map<String, Object>> books() {
        return List.of(Map.of("id", 1, "title", "The Pragmatic Programmer"));
    }

    // Requires SCOPE_books:write. A read-only token reaches here and is refused with 403.
    @DeleteMapping("/api/books/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        return Map.of("deleted", id);
    }
}
