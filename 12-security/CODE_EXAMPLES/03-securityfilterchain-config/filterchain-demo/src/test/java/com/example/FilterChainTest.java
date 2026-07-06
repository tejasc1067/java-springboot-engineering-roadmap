package com.example;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// spring-security-test wires the real SecurityFilterChain into MockMvc, so these assertions exercise the
// actual authorization rules from SecurityConfig. .with(user("alice")) injects an already-authenticated
// user (no password round-trip needed), which is exactly what you want when testing *authorization*.
@SpringBootTest
@AutoConfigureMockMvc
class FilterChainTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicRouteIsOpenToAnonymous() throws Exception {
        mvc.perform(get("/public/health"))
                .andExpect(status().isOk());
    }

    @Test
    void apiRouteRejectsAnonymousWith401() throws Exception {
        // Not authenticated, and /api/** falls under anyRequest().authenticated() -> 401 (HTTP Basic entry point).
        mvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiRouteAllowsAnAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/books").with(user("alice")))
                .andExpect(status().isOk());
    }
}
