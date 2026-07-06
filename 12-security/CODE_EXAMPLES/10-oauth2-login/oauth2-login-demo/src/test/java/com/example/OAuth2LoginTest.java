package com.example;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// What IS deterministic offline: the flow's START (the app building the redirect to Google) and the app's use
// of the identity AFTER login (simulated with oidcLogin(), which injects an OidcUser without a real round
// trip). The middle — exchanging the code for tokens at Google — needs the real provider and is exercised
// manually (see the demo README), not here.
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2LoginTest {

    @Autowired
    MockMvc mvc;

    @Test
    void homeIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void protectedRouteRedirectsWhenAnonymous() throws Exception {
        // Not logged in -> the entry point sends the browser into the login flow (a 3xx redirect), it does not
        // return 401/403 like the token topics. This is a browser app.
        mvc.perform(get("/api/me")).andExpect(status().is3xxRedirection());
    }

    @Test
    void authorizationEndpointRedirectsToGoogleWithCodeFlowParams() throws Exception {
        // The heart of the authorization-code flow's first step: the app builds a redirect to Google's
        // authorization endpoint with response_type=code, its client_id, the redirect_uri, the requested
        // scopes, and a state value (CSRF protection for the flow). No network call — the app constructs this.
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", containsString("response_type=code")))
                .andExpect(header().string("Location", containsString("client_id=")))
                .andExpect(header().string("Location", containsString("scope=openid")))
                .andExpect(header().string("Location", containsString("redirect_uri=")))
                .andExpect(header().string("Location", containsString("state=")));
    }

    @Test
    void afterLoginMeReturnsTheOidcIdentity() throws Exception {
        // oidcLogin() stands in for a completed login: it injects an OidcUser with the given id_token claims.
        // Proves the app reads the provider-supplied identity — the point of logging in.
        mvc.perform(get("/api/me").with(oidcLogin().idToken(token -> token
                        .subject("google-user-123")
                        .claim("email", "alice@example.com")
                        .claim("name", "Alice Example"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("google-user-123"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice Example"));
    }
}
