package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class BeanValidationTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void validRequestReturns201() throws Exception {
        String body = """
                {
                  "title": "The Pragmatic Programmer",
                  "author": "Hunt & Thomas",
                  "isbn": "9780201616224",
                  "publisher": { "name": "Addison-Wesley" },
                  "tags": ["classic", "engineering"]
                }
                """;
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("The Pragmatic Programmer"));
    }

    @Test
    void blankTitleAndBadIsbnReturn400WithFieldErrors() throws Exception {
        String body = """
                {
                  "title": "",
                  "author": "Hunt & Thomas",
                  "isbn": "123",
                  "publisher": { "name": "Addison-Wesley" },
                  "tags": ["classic"]
                }
                """;
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                // Both failing fields are surfaced — this is the whole point of the topic.
                .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.hasItem("title")))
                .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.hasItem("isbn")))
                // The custom message attribute shows up verbatim.
                .andExpect(jsonPath("$.fieldErrors[*].message", Matchers.hasItem("ISBN must be 13 characters")));
    }

    @Test
    void blankNestedPublisherNameReportsDottedPath() throws Exception {
        // The inner @NotBlank only runs because CreateBookRequest marks the field @Valid.
        // The error path is the dotted "publisher.name", not just "name".
        String body = """
                {
                  "title": "Clean Code",
                  "author": "Martin",
                  "isbn": "9780132350884",
                  "publisher": { "name": "" },
                  "tags": ["classic"]
                }
                """;
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.hasItem("publisher.name")));
    }

    @Test
    void blankCollectionElementReportsIndexedPath() throws Exception {
        // The @NotBlank is on the element type; an empty tag reports as "tags[1]".
        String body = """
                {
                  "title": "Clean Code",
                  "author": "Martin",
                  "isbn": "9780132350884",
                  "publisher": { "name": "Prentice Hall" },
                  "tags": ["classic", ""]
                }
                """;
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.hasItem("tags[1]")));
    }
}
