package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
    void restControllerReturnsJsonList() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }

    @Test
    void restControllerReturnsJsonSingle() throws Exception {
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.author").value("Bloch"));
    }

    @Test
    void controllerPlusResponseBodyIsEquivalentToRestController() throws Exception {
        mockMvc.perform(get("/legacy/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].title").value("Effective Java"));
    }

    @Test
    void plainControllerDoesNotReturnJson() throws Exception {
        // @Controller without @ResponseBody: Spring tries to interpret the return
        // value as a view name. With no template engine on the classpath, the
        // response is not a successful JSON 200. Exact status depends on Spring's
        // fallback resolution; we just assert it is NOT a JSON 200.
        MvcResult result = mockMvc.perform(get("/broken/books")).andReturn();
        int status = result.getResponse().getStatus();
        String contentType = result.getResponse().getContentType();

        boolean isJsonOk = status == 200
                && contentType != null
                && contentType.toLowerCase().contains("json");
        assertThat(isJsonOk)
                .as("plain @Controller without @ResponseBody should not produce a JSON 200")
                .isFalse();
    }
}
