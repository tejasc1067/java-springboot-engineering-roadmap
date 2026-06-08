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
class QueryParametersControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void defaultPagingReturnsFirstPage() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void explicitPagingSlicesResults() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void filterByAuthor() throws Exception {
        mockMvc.perform(get("/api/books").param("author", "Bloch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }

    @Test
    void multiTagAsRepeatedParam() throws Exception {
        mockMvc.perform(get("/api/books").param("tag", "java").param("tag", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Spring in Action"));
    }

    @Test
    void multiTagAsCommaSeparated() throws Exception {
        mockMvc.perform(get("/api/books").param("tag", "java,spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void requiredParameterMissingGives400() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void intConversionFailureGives400() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debugReturnsAllParameters() throws Exception {
        mockMvc.perform(get("/api/debug").param("foo", "1").param("bar", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foo").value("1"))
                .andExpect(jsonPath("$.bar").value("2"));
    }
}
