package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// This demo deliberately has NO exception advice — its point is the raw default behavior, including a
// genuine 500 for an unmapped exception. MockMvc rethrows an unhandled exception instead of producing a
// response, so we exercise the real servlet stack with a running server + TestRestTemplate, which returns
// the actual status the way a real HTTP client would see it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookControllerTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void presentBookReturns200() {
        ResponseEntity<String> response = rest.getForEntity("/api/books/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void missingBookReturns404() {
        // BookNotFoundException is @ResponseStatus(NOT_FOUND), so escaping it produces 404.
        ResponseEntity<String> response = rest.getForEntity("/api/books/999", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unmappedExceptionReturns500() {
        // IllegalStateException has no status mapping, so it falls through to 500.
        ResponseEntity<String> response = rest.getForEntity("/api/books/explode", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
