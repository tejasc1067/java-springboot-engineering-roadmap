package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class BookControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void fullLifecycleWithStatusesAndLocation() throws Exception {
        // POST -> 201 + Location header
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"EJ\",\"author\":\"Bloch\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/books/1")))
                .andExpect(jsonPath("$.id").value(1));

        // GET present -> 200
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Bloch"));

        // GET missing -> 404, no body
        mockMvc.perform(get("/api/books/9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        // DELETE present -> 204, no body
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // DELETE missing -> 404
        mockMvc.perform(delete("/api/books/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listEndpointIsAlways200() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk());
    }
}
