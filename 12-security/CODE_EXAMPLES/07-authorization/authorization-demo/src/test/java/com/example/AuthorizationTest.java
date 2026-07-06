package com.example;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// alice = ROLE_USER, admin = ROLE_ADMIN. The assertions are the 401-vs-403 distinction from topic 01 made
// real: anonymous -> 401 (who are you?), authenticated-but-wrong-role -> 403 (you may not do this).
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousIsUnauthenticated401() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void anyAuthenticatedUserCanReadBooks() throws Exception {
        mvc.perform(get("/api/books").with(httpBasic("alice", "password"))).andExpect(status().isOk());
        mvc.perform(get("/api/books").with(httpBasic("admin", "password"))).andExpect(status().isOk());
    }

    // --- URL-based authorization: /api/admin/** hasRole('ADMIN') ---

    @Test
    void userIsForbiddenFromAdminUrl403() throws Exception {
        mvc.perform(get("/api/admin/stats").with(httpBasic("alice", "password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsAllowedOnAdminUrl() throws Exception {
        mvc.perform(get("/api/admin/stats").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    // --- Method-based authorization: @PreAuthorize("hasRole('ADMIN')") on delete ---

    @Test
    void userIsForbiddenFromDelete403() throws Exception {
        // alice is authenticated (passes the URL rule) but not ADMIN, so @PreAuthorize denies -> 403.
        mvc.perform(delete("/api/books/1").with(httpBasic("alice", "password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsAllowedToDelete() throws Exception {
        mvc.perform(delete("/api/books/1").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    // --- Expression-based ownership: hasRole('ADMIN') or #username == authentication.name ---

    @Test
    void userCanSeeOwnProfile() throws Exception {
        mvc.perform(get("/api/users/alice/profile").with(httpBasic("alice", "password")))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotSeeAnotherUsersProfile403() throws Exception {
        mvc.perform(get("/api/users/bob/profile").with(httpBasic("alice", "password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSeeAnyProfile() throws Exception {
        mvc.perform(get("/api/users/bob/profile").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }
}
