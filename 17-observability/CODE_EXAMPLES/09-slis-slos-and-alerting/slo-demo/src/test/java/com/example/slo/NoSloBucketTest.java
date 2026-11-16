package com.example.slo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** Broken: a latency rule written against le="0.3" when the app doesn't export that bucket. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.metrics.distribution.slo.http.server.requests=")
@AutoConfigureObservability
class NoSloBucketTest {

    @Autowired TestRestTemplate http;

    @Test
    void withoutTheSloSetting_thereAreNoBuckets_soTheLatencyRuleMatchesNothingAndNeverFires() {
        http.getForEntity("/orders/1", String.class);

        String metrics = http.getForObject("/actuator/prometheus", String.class);

        assertThat(metrics).contains("http_server_requests_seconds_count{");
        assertThat(metrics).doesNotContain("http_server_requests_seconds_bucket");
    }
}
