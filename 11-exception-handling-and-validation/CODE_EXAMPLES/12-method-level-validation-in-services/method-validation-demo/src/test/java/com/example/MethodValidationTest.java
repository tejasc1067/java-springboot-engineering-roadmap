package com.example;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class MethodValidationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    LibraryService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void businessRuleFailureReturns409() throws Exception {
        // Book 2 is seeded with 0 copies. The request is well-formed (passes input validation),
        // but the domain can't satisfy it -> InsufficientCopiesException -> 409.
        mockMvc.perform(post("/api/library/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":2,\"copies\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient copies"));
    }

    @Test
    void validInputBorrowsSuccessfully() throws Exception {
        // Book 1 is seeded with 3 copies; borrowing 2 leaves 1.
        mockMvc.perform(post("/api/library/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":1,\"copies\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(1));
    }

    @Test
    void callingServiceDirectlyWithBadArgsThrowsConstraintViolation() {
        // The defensive guard fires on a DIRECT (non-HTTP) call: @Positive rejects copies = 0
        // at the service boundary, thrown by Spring's MethodValidationPostProcessor proxy.
        assertThatThrownBy(() -> service.borrow(1L, 0))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
