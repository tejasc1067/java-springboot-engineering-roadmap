package com.example.slo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * The rules in rules/ are only as good as the metric names in them. This checks those names against
 * what Spring Boot really exports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability   // tests disable metrics export by default; without this /actuator/prometheus is 404
class MetricNamesTest {

    @Autowired TestRestTemplate http;

    @Test
    void theSeriesTheRulesUse_existWithTheseExactNamesAndLabels() {
        http.getForEntity("/orders/1", String.class);
        http.getForEntity("/orders/0", String.class);    // 503
        http.getForEntity("/orders/-1", String.class);   // 404

        String metrics = http.getForObject("/actuator/prometheus", String.class);

        assertThat(metrics)
                .contains("http_server_requests_seconds_count{error=\"none\",exception=\"none\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/orders/{id}\"}")
                .contains("status=\"503\",uri=\"/orders/{id}\"")
                // 404 is a client error: it counts as a valid request, not a failure, in the availability SLI
                .contains("status=\"404\",uri=\"/orders/{id}\"")
                // the 300 ms boundary from management.metrics.distribution.slo
                .contains("http_server_requests_seconds_bucket{error=\"none\",exception=\"none\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/orders/{id}\",le=\"0.3\"}")
                // only that boundary and +Inf: the slo setting doesn't turn on a full histogram
                .doesNotContain("le=\"0.1\"");
    }
}
