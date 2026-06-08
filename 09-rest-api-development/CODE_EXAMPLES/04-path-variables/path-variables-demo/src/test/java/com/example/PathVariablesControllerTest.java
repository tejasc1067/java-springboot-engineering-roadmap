package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PathVariablesControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void longPathVariableBinds() throws Exception {
        mockMvc.perform(get("/api/books/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void longConversionFailureGives400() throws Exception {
        mockMvc.perform(get("/api/books/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoPathVariablesBindByName() throws Exception {
        mockMvc.perform(get("/api/books/7/reviews/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(7))
                .andExpect(jsonPath("$.reviewId").value(3));
    }

    @Test
    void regexMatchSucceeds() throws Exception {
        mockMvc.perform(get("/api/users/alice_99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_99"));
    }

    @Test
    void regexMismatchGives404() throws Exception {
        // Uppercase fails the regex -> no handler matches -> 404 (not 400).
        mockMvc.perform(get("/api/users/Alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void localDatePathBinds() throws Exception {
        mockMvc.perform(get("/api/events/by-date/2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2025-12-31"));
    }

    @Test
    void invalidDateGives400() throws Exception {
        mockMvc.perform(get("/api/events/by-date/not-a-date"))
                .andExpect(status().isBadRequest());
    }
}
