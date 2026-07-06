package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Proves the difference between the two chains using spring-security-test (which handles form login + CSRF).
// The stateful chain remembers you via a session; the stateless chain remembers nothing.
@SpringBootTest
@AutoConfigureMockMvc
class SessionVsStatelessTest {

    @Autowired
    MockMvc mvc;

    // --- STATEFUL (form login) ---

    @Test
    void formLoginCreatesAReusableSession() throws Exception {
        // Log in once. This authenticates AND creates an HttpSession holding the SecurityContext.
        MvcResult login = mvc.perform(formLogin("/login").user("alice").password("password"))
                .andExpect(authenticated().withUsername("alice"))
                .andReturn();

        HttpSession session = login.getRequest().getSession(false);
        assertThat(session).isNotNull();   // a session was created for us

        // A later request carrying ONLY that session (no credentials resent) is authenticated by it.
        mvc.perform(get("/stateful/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousToStatefulIsRedirectedToTheLoginPage() throws Exception {
        // Form login's answer to "who are you?" is a browser redirect to /login, not a 401.
        mvc.perform(get("/stateful/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // --- STATELESS (HTTP Basic) ---

    @Test
    void statelessBasicAuthenticatesPerRequest() throws Exception {
        mvc.perform(get("/stateless/me").with(httpBasic("alice", "password")))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousToStatelessIs401() throws Exception {
        // No form login here, so the answer is a clean 401 challenge, not a redirect.
        mvc.perform(get("/stateless/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statelessCreatesNoSession() throws Exception {
        MvcResult result = mvc.perform(get("/stateless/me").with(httpBasic("alice", "password")))
                .andExpect(status().isOk())
                .andReturn();
        // STATELESS policy: even a successful request leaves no server-side session behind.
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
