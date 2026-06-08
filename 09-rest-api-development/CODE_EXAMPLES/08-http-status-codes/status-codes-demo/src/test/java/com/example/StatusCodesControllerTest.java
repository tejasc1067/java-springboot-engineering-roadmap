package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class StatusCodesControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void ok200() throws Exception {
        mockMvc.perform(get("/api/status/ok")).andExpect(status().isOk());
    }

    @Test
    void created201WithLocation() throws Exception {
        mockMvc.perform(get("/api/status/created"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void accepted202() throws Exception {
        mockMvc.perform(get("/api/status/accepted"))
                .andExpect(status().isAccepted());
    }

    @Test
    void noContent204IsEmptyBody() throws Exception {
        mockMvc.perform(get("/api/status/no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void badRequest400() throws Exception {
        mockMvc.perform(get("/api/status/bad-request"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthorized401() throws Exception {
        mockMvc.perform(get("/api/status/unauthorized"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidden403() throws Exception {
        mockMvc.perform(get("/api/status/forbidden"))
                .andExpect(status().isForbidden());
    }

    @Test
    void notFound404() throws Exception {
        mockMvc.perform(get("/api/status/not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conflict409() throws Exception {
        mockMvc.perform(get("/api/status/conflict"))
                .andExpect(status().isConflict());
    }

    @Test
    void unprocessable422() throws Exception {
        mockMvc.perform(get("/api/status/unprocessable"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void serverError500FromThrownResponseStatusException() throws Exception {
        mockMvc.perform(get("/api/status/server-error"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void unavailable503WithRetryAfter() throws Exception {
        mockMvc.perform(get("/api/status/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void wrongMethod405() throws Exception {
        // Endpoint /ok is GET only; PUT must return 405.
        mockMvc.perform(put("/api/status/ok"))
                .andExpect(status().isMethodNotAllowed());
    }
}
