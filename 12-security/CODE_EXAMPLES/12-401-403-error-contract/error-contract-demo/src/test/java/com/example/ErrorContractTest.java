package com.example;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// The point of the topic: a 401 (from the AuthenticationEntryPoint), a 403 (from the AccessDeniedHandler), and
// a 404 (from the @RestControllerAdvice) all come back as the SAME RFC 7807 ProblemDetail shape and media type.
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractTest {

    @Autowired
    MockMvc mvc;

    private static final String PROBLEM_JSON = "application/problem+json";

    @Test
    void unauthenticatedIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/books"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void forbiddenIs403ProblemDetail() throws Exception {
        // alice is authenticated (USER) but not ADMIN -> AccessDeniedHandler -> 403 ProblemDetail.
        mvc.perform(get("/api/admin/stats").with(httpBasic("alice", "password")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/api/admin/stats"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void notFoundIs404ProblemDetailFromAdvice() throws Exception {
        // A missing book -> BookNotFoundException -> @RestControllerAdvice. Same envelope, different origin.
        mvc.perform(get("/api/books/999").with(httpBasic("alice", "password")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Book not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/books/999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void authorizedRequestsSucceed() throws Exception {
        mvc.perform(get("/api/books").with(httpBasic("alice", "password"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/stats").with(httpBasic("admin", "password"))).andExpect(status().isOk());
        mvc.perform(get("/api/books/1").with(httpBasic("alice", "password"))).andExpect(status().isOk());
    }
}
