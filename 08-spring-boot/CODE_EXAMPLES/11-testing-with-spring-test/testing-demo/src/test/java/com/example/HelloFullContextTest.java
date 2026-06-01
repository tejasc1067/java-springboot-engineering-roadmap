package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context test: real HelloService bean, real DispatcherServlet,
 * real Tomcat on a random port, real HTTP round-trip via TestRestTemplate.
 *
 * Use this style when the test's purpose is "does the whole stack wire up?".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloFullContextTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void getHelloReturnsTheRealServicesGreeting() {
        ResponseEntity<String> response = rest.getForEntity("/hello", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, World!");
    }
}
