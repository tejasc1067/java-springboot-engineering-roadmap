package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BookControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // One method walks the whole CRUD lifecycle. The BookController bean is a
    // singleton with mutable state, so splitting into multiple test methods
    // would leak state between them. A single sequential test is simpler than
    // @DirtiesContext or reflective resets, and the lesson is the verb shapes.
    @Test
    void fullCrudLifecycle() throws Exception {
        // POST: create
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Effective Java\",\"author\":\"Bloch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Effective Java"));

        // POST: a second one, to exercise the listing
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Code\",\"author\":\"Martin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));

        // GET list
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // GET one
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Bloch"));

        // PUT: full replace - author is omitted, so it becomes null
        mockMvc.perform(put("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"EJ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("EJ"))
                .andExpect(jsonPath("$.author").doesNotExist());

        // PATCH: partial update preserves untouched fields
        mockMvc.perform(patch("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Joshua Bloch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("EJ"))
                .andExpect(jsonPath("$.author").value("Joshua Bloch"));

        // DELETE
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isOk());

        // List now has one entry
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
