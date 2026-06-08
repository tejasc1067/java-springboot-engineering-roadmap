package com.example;

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
class BookControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void jacksonBindsJsonToRecord() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Effective Java\",\"author\":\"Bloch\",\"published_year\":2017}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Effective Java"))
                .andExpect(jsonPath("$.author").value("Bloch"))
                .andExpect(jsonPath("$.published_year").value(2017));
    }

    @Test
    void missingFieldsBecomeNull() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").doesNotExist())
                .andExpect(jsonPath("$.published_year").doesNotExist());
    }

    @Test
    void unknownFieldsAreIgnoredByDefault() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"secret\":\"evil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.title").value("X"));
    }

    @Test
    void wrongContentTypeGives415() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void malformedJsonGives400() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }
}
