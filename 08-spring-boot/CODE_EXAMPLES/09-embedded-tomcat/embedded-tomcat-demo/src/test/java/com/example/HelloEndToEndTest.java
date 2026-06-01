package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RANDOM_PORT starts the embedded Tomcat on a free port and exposes it
 * to TestRestTemplate, so this test makes a real HTTP round-trip.
 *
 * MockMvc (topic 11) is the faster alternative that skips the network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloEndToEndTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void getHelloReturnsTwoHundredAndTheGreeting() {
        ResponseEntity<String> response = rest.getForEntity("/hello", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, World!");
    }
}
