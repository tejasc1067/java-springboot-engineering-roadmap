package com.example;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Slice test: only BookController + MVC infrastructure. BookService is mocked.
// Spring starts up dramatically faster -- it skips every non-web bean.
@WebMvcTest(BookController.class)
class BookControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookService service;

    @Test
    void listReturnsWhateverTheMockSays() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                new Book(99L, "Mock Title", "Mock Author")));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Mock Title"));
    }

    @Test
    void getOneFromMock() throws Exception {
        when(service.findById(7L)).thenReturn(Optional.of(
                new Book(7L, "Mocked", "Author")));

        mockMvc.perform(get("/api/books/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void emptyOptionalGives404() throws Exception {
        when(service.findById(9999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/books/9999"))
                .andExpect(status().isNotFound());
    }
}
