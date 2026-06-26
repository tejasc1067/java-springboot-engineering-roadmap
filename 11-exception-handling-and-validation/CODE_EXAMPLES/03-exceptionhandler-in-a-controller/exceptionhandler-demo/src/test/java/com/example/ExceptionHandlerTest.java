package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// ReportController throws the same exception as BookController but has no handler, so it falls through to a
// real 500. MockMvc would rethrow that unhandled exception rather than return a response, so we use a
// running server + TestRestTemplate to observe the actual status, the way a real client would.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExceptionHandlerTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void handledControllerReturns404WithBody() {
        // BookController's local @ExceptionHandler runs: 404 plus a ProblemDetail body.
        ResponseEntity<String> response = rest.getForEntity("/api/books/999", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .contains("book 999 not found")
                .contains("Book not found");
    }

    @Test
    void unhandledControllerReturns500() {
        // ReportController throws the same exception but has no handler -> 500.
        ResponseEntity<String> response = rest.getForEntity("/api/reports/999", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
