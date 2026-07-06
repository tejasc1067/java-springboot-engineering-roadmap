package com.example;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// A tour of spring-security-test. The theme: choose your tool by WHAT you're testing.
//  - Testing authorization rules / controller logic given a user -> inject a mock context (@WithMockUser, user()).
//  - Testing that authentication itself works (password, encoder, token) -> send REAL credentials (httpBasic()).
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTestingTest {

    @Autowired
    MockMvc mvc;

    // ---------------------------------------------------------------------
    // The three outcomes every secured endpoint has: 401 (who are you?),
    // 403 (you may not), 200 (ok).
    // ---------------------------------------------------------------------

    @Test
    void anonymousIsUnauthorized401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser   // default: username "user", role USER — a SecurityContext is injected before the test runs
    void authenticatedUserIsOk200() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("user"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void wrongRoleIsForbidden403() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // @WithMockUser variations — and the roles-vs-authorities distinction (topic 07).
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")   // roles="ADMIN" -> authority ROLE_ADMIN (prefix added)
    void adminRoleReachesAdminArea() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")   // authorities=... is used VERBATIM — no ROLE_ prefix added
    void authoritiesAttributeIsUsedAsIs() throws Exception {
        // Same effect as roles="ADMIN" here, but a common bug is writing authorities="ADMIN" (missing ROLE_)
        // and then hasRole('ADMIN') never matches. roles="ADMIN" and authorities="ROLE_ADMIN" are equivalent.
        mvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser   // explicit anonymous — clearer than "no annotation" when the intent matters
    void explicitAnonymousIsUnauthorized401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // Request post-processors: per-request, no annotation. Handy for parameterized
    // tests or when different requests in one method need different users.
    // ---------------------------------------------------------------------

    @Test
    void perRequestMockUser() throws Exception {
        // user(...) injects a mock context just like @WithMockUser, but scoped to this one request.
        mvc.perform(get("/api/admin/stats").with(user("someone").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void realCredentialsRunTheAuthenticationChain() throws Exception {
        // httpBasic(...) sends a real Authorization header, so this goes through the UserDetailsService +
        // BCryptPasswordEncoder — the actual login path, not a mock.
        mvc.perform(get("/api/admin/stats").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Mock context vs real credentials — the key lesson. A mock user cannot catch a
    // broken password check; only a real-credential test can.
    // ---------------------------------------------------------------------

    @Test
    void wrongPasswordIsRejected401() throws Exception {
        // @WithMockUser would happily "log in" here and hide a broken encoder. Real credentials expose it.
        mvc.perform(get("/api/me").with(httpBasic("alice", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // CSRF: state-changing requests need a token when CSRF protection is on.
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser
    void postWithoutCsrfTokenIsForbidden403() throws Exception {
        // Authentication is present (mock user) but there's no CSRF token -> 403. csrf() is independent of auth.
        mvc.perform(post("/api/books")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postWithCsrfTokenSucceeds() throws Exception {
        mvc.perform(post("/api/books").with(csrf())).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------
    // Method security (@PreAuthorize) is driven by the mock user's role too.
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "USER")
    void methodSecurityDeniesNonAdmin403() throws Exception {
        mvc.perform(delete("/api/books/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityAllowsAdmin() throws Exception {
        mvc.perform(delete("/api/books/1").with(csrf())).andExpect(status().isOk());
    }
}
