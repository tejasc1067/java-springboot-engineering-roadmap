package com.example;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    // Public. In a real UI this page would have a "Login with Google" button whose link is simply
    // /oauth2/authorization/google — that's all a login button is.
    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Public home. GET /api/me and you'll be redirected to Google to sign in.");
    }

    // Protected. Because Google is an OIDC provider, the authenticated principal is an OidcUser built from the
    // id_token the provider returned — the app never saw the user's Google password. We read standard OIDC
    // claims (sub, email, name) straight off it. Contrast topic 09, where the app RECEIVED a token from the
    // caller; here the app OBTAINED the identity by driving the login itself.
    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "subject", user.getSubject(),
                "email", String.valueOf(user.getEmail()),
                "name", String.valueOf(user.getFullName()));
    }
}
