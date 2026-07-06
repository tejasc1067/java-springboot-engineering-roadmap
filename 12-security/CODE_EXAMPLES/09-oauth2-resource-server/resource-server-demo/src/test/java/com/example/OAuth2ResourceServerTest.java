package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Tokens are minted by TokenIssuer (the "IdP") and sent as real bearer headers, so every case exercises the
// actual signature check against app.pub — not a mocked principal. That is what proves the resource server
// validates tokens it did not issue.
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2ResourceServerTest {

    @Autowired
    MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void publicRouteNeedsNoToken() throws Exception {
        mvc.perform(get("/public/hello")).andExpect(status().isOk());
    }

    @Test
    void protectedRouteWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenExposesItsClaims() throws Exception {
        String token = TokenIssuer.issue("alice", "books:read");
        mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("alice"))
                .andExpect(jsonPath("$.issuer").value("https://demo-authserver.example"));
    }

    @Test
    void readScopeCanReadBooks() throws Exception {
        String token = TokenIssuer.issue("alice", "books:read");
        mvc.perform(get("/api/books").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void readOnlyScopeCannotDelete403() throws Exception {
        // Authenticated with a valid token, but the "scope" claim lacks books:write -> 403, not 401.
        String token = TokenIssuer.issue("alice", "books:read");
        mvc.perform(delete("/api/books/1").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeScopeCanDelete() throws Exception {
        String token = TokenIssuer.issue("alice", "books:read books:write");
        mvc.perform(delete("/api/books/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejected401() throws Exception {
        String token = TokenIssuer.issueExpired("alice", "books:read");
        mvc.perform(get("/api/books").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected401() throws Exception {
        // A well-formed token with valid claims, but signed by a key the app's public key can't verify.
        // This is the forgery case: without the issuer's private key you cannot produce a token this app trusts.
        String token = TokenIssuer.issueWithForeignKey("attacker", "books:read books:write");
        mvc.perform(get("/api/books").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsRejected401() throws Exception {
        mvc.perform(get("/api/books").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
