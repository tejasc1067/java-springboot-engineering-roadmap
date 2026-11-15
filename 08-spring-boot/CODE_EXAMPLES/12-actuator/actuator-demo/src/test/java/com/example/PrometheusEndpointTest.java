package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot switches metrics export OFF in tests by default, so without
 * @AutoConfigureObservability /actuator/prometheus would be a 404 here even
 * though it works in the running app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
class PrometheusEndpointTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void customCounterAndHttpTimerShowUpInPrometheusFormat() {
        rest.getForObject("/hello", String.class);
        rest.getForObject("/hello", String.class);

        String scrape = rest.getForObject("/actuator/prometheus", String.class);

        // Dots become underscores and counters get a _total suffix. Grepping
        // Prometheus for "greetings.sent" finds nothing.
        assertThat(scrape)
                .contains("greetings_sent_total 2.0")
                .doesNotContain("greetings.sent");
        // Boot times every request with no code: one series per uri/method/status.
        assertThat(scrape).containsPattern(
                "http_server_requests_seconds_count\\{.*uri=\"/hello\".*} 2");
    }
}
