package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class PitfallsTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    private static final String BLANK_BODY = "{\"username\":\"\",\"email\":\"\"}";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // PITFALL 1 — without @Valid, blank input is accepted: 201, bug shipped.
    @Test
    void missingValid_acceptsBadInput_returns201() throws Exception {
        mockMvc.perform(post("/api/users/without-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BLANK_BODY))
                .andExpect(status().isCreated());
    }

    // FIX — with @Valid, the same blank input is rejected with a 400 and per-field errors.
    @Test
    void withValid_rejectsBadInput_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users/with-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BLANK_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").exists());
    }

    // PITFALL 6 — param validation throws a DIFFERENT exception (ConstraintViolationException),
    // yet the advice returns the SAME envelope: 400 with a fieldErrors array.
    @Test
    void paramValidation_returnsSameShapeAsBody() throws Exception {
        mockMvc.perform(get("/api/users/0"))   // @Min(1) violated
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    // PITFALL 3 — the swallowing endpoint hides the failure: 200 "ok" on a charge that threw.
    @Test
    void swallowingHandler_hidesFailure_returns200Ok() throws Exception {
        mockMvc.perform(get("/api/payments/swallow"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    // FIX — letting it propagate surfaces a real 500 with the generic contract body
    // (and the cause is logged, never leaked: pitfall 7).
    @Test
    void propagatingHandler_surfacesFailure_returns500Generic() throws Exception {
        mockMvc.perform(get("/api/payments/propagate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("payment gateway timed out"))));
    }
}
