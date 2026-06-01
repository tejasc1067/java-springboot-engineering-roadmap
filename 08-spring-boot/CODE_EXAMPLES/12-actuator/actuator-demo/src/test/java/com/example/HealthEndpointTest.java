package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls /actuator/health over real HTTP and asserts the aggregate status is UP.
 * With show-details: always, the response body also contains the per-component
 * statuses (diskSpace, ping) which we assert as a smoke test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void actuatorHealthReturnsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"status\":\"UP\"")
                .contains("diskSpace")
                .contains("ping");
    }
}
