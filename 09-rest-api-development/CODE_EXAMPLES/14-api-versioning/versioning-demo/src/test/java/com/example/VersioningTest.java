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
class VersioningTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // --- URI versioning ---

    @Test
    void uriV1ReturnsV1Shape() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Effective Java by Bloch"))
                .andExpect(jsonPath("$[0].title").doesNotExist());
    }

    @Test
    void uriV2ReturnsV2Shape() throws Exception {
        mockMvc.perform(get("/api/v2/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Effective Java"))
                .andExpect(jsonPath("$[0].author").value("Bloch"))
                .andExpect(jsonPath("$[0].name").doesNotExist());
    }

    // --- Header versioning ---

    @Test
    void headerV1() throws Exception {
        mockMvc.perform(get("/api/header-books").header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Effective Java by Bloch"));
    }

    @Test
    void headerV2() throws Exception {
        mockMvc.perform(get("/api/header-books").header("X-API-Version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }

    @Test
    void headerMissingFailsToRoute() throws Exception {
        // No handler matches when the header is absent -> 404.
        mockMvc.perform(get("/api/header-books"))
                .andExpect(status().isNotFound());
    }

    // --- Media-type versioning ---

    @Test
    void mediaTypeV1() throws Exception {
        mockMvc.perform(get("/api/media-books").header("Accept", "application/vnd.example.v1+json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Effective Java by Bloch"));
    }

    @Test
    void mediaTypeV2() throws Exception {
        mockMvc.perform(get("/api/media-books").header("Accept", "application/vnd.example.v2+json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }

    @Test
    void mediaTypeUnknownAcceptIs406() throws Exception {
        // Asking for application/json (no version) doesn't match either producer -> 406.
        mockMvc.perform(get("/api/media-books").header("Accept", "application/json"))
                .andExpect(status().isNotAcceptable());
    }
}
