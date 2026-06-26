package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ParamValidationTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // A bad @PathVariable (id < 1) throws ConstraintViolationException, mapped to the unified shape.
    @Test
    void invalidPathVariableReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/api/books/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                .andExpect(jsonPath("$.fieldErrors[0].message", Matchers.notNullValue()));
    }

    // A bad @RequestParam (size > 100) also throws ConstraintViolationException.
    @Test
    void invalidRequestParamReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/api/books").param("size", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].message", Matchers.notNullValue()));
    }

    // A bad @Valid @RequestBody throws MethodArgumentNotValidException — a DIFFERENT exception, yet the
    // advice produces the SAME shape: title "Validation failed" + a fieldErrors array of {field,message}.
    @Test
    void invalidBodyReturns400WithSameFieldErrorsShape() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message", Matchers.notNullValue()));
    }

    // Sanity: valid input still flows through.
    @Test
    void validRequestsSucceed() throws Exception {
        mockMvc.perform(get("/api/books/1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/books").param("size", "20")).andExpect(status().isOk());
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Code\"}"))
                .andExpect(status().isCreated());
    }
}
