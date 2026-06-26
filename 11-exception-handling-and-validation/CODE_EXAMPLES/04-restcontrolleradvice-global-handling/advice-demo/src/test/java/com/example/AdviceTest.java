package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AdviceTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void bookControllerNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("book 999 not found"));
    }

    @Test
    void reportControllerNotFoundAlsoReturns404() throws Exception {
        // The same global handler now covers a different controller — the topic 03 scope limit is gone.
        mockMvc.perform(get("/api/reports/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("book 999 not found"));
    }

    @Test
    void duplicateIsbnReturns409() throws Exception {
        // ISBN 9780201616224 is already seeded, so creating it again conflicts.
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dup\",\"author\":\"X\",\"isbn\":\"9780201616224\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate ISBN"));
    }

    @Test
    void newIsbnReturns201() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Code\",\"author\":\"Martin\",\"isbn\":\"9780132350884\"}"))
                .andExpect(status().isCreated());
    }
}
