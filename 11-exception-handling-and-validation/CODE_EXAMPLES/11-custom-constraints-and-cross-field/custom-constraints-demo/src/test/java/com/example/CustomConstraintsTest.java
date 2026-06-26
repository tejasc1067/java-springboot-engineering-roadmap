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
class CustomConstraintsTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void validIsbnAndDateRangeReturns201() throws Exception {
        // 9780132350884 is a real ISBN-13 (weighted-sum checksum divisible by 10); range is valid.
        String body = """
                {"title":"Effective Java",
                 "isbn":"9780132350884",
                 "loanPeriod":{"startDate":"2026-01-01","endDate":"2026-01-15"}}""";
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("9780132350884"));
    }

    @Test
    void badIsbnChecksumReturns400WithIsbnFieldError() throws Exception {
        // Same digits but the check digit is 5 instead of 4 -> weighted sum is no longer a multiple of 10.
        String body = """
                {"title":"Effective Java",
                 "isbn":"9780132350885",
                 "loanPeriod":{"startDate":"2026-01-01","endDate":"2026-01-15"}}""";
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.hasItem("isbn")));
    }

    @Test
    void endDateBeforeStartDateReturns400WithEndDateFieldError() throws Exception {
        // ISBN is valid; the cross-field rule fails. The violation is re-attached to endDate, so the
        // field path is the nested "loanPeriod.endDate".
        String body = """
                {"title":"Effective Java",
                 "isbn":"9780132350884",
                 "loanPeriod":{"startDate":"2026-01-15","endDate":"2026-01-01"}}""";
        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", Matchers.hasItem("loanPeriod.endDate")));
    }
}
