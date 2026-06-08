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
class BookControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void defaultsReturnAllSeven() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(7))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void smallPageSize() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "0").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void lastPageIsMarkedLast() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "3").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void filterByAuthor() throws Exception {
        mockMvc.perform(get("/api/books").param("author", "Bloch"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].author").value("Bloch"));
    }

    @Test
    void sortByYearAsc() throws Exception {
        mockMvc.perform(get("/api/books").param("sort", "year,asc"))
                .andExpect(jsonPath("$.content[0].year").value(1999))
                .andExpect(jsonPath("$.content[6].year").value(2018));
    }

    @Test
    void multipleSortFields() throws Exception {
        // Sort by author asc, then year desc within each author.
        // Authors alphabetically: Bloch, Evans, Fowler, Goetz, Hunt, Martin.
        // For Bloch, years are 2017, 2006 -> 2017 should come first (desc).
        mockMvc.perform(get("/api/books")
                        .param("sort", "author,asc")
                        .param("sort", "year,desc"))
                .andExpect(jsonPath("$.content[0].author").value("Bloch"))
                .andExpect(jsonPath("$.content[0].year").value(2017))
                .andExpect(jsonPath("$.content[1].author").value("Bloch"))
                .andExpect(jsonPath("$.content[1].year").value(2006));
    }

    @Test
    void hostileSizeIsClamped() throws Exception {
        mockMvc.perform(get("/api/books").param("size", "1000000"))
                // size clamps to 100, totalElements remains 7
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(7));
    }

    @Test
    void unknownSortFieldGives400() throws Exception {
        mockMvc.perform(get("/api/books").param("sort", "secret,desc"))
                .andExpect(status().isBadRequest());
    }
}
