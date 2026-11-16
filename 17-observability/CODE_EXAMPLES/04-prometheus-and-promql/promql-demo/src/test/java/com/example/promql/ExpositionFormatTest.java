package com.example.promql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** No Docker: reads what the app hands to Prometheus on every scrape. */
@AutoConfigureObservability   // Spring Boot tests turn metrics export (and /actuator/prometheus) off unless asked
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpositionFormatTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void aCounterIsExposedWithTheTotalSuffix_andAGaugeWithout() {
        http.postForObject("/orders", null, String.class);
        http.postForObject("/orders", null, String.class);
        String text = scrape();

        assertThat(text).contains("# TYPE orders_placed_total counter");
        assertThat(line(text, "orders_placed_total ")).isEqualTo("orders_placed_total 2.0");
        assertThat(text).contains("# TYPE orders_pending gauge");
        assertThat(line(text, "orders_pending ")).startsWith("orders_pending ");
    }

    @Test
    void aTimerWithAHistogram_becomesCumulativeBuckets_plusCountAndSum() {
        for (int i = 0; i < 3; i++) http.getForObject("/orders/42", String.class);
        String text = scrape();

        List<String> buckets = lines(text, "http_server_requests_seconds_bucket{")
                .stream().filter(l -> l.contains("uri=\"/orders/{id}\"") && l.contains("status=\"200\"")).toList();
        assertThat(buckets).isNotEmpty();

        // Buckets are cumulative: "le" means "less than or equal", so each count includes all faster requests.
        double previous = 0;
        for (String bucket : buckets) {
            double count = value(bucket);
            assertThat(count).isGreaterThanOrEqualTo(previous);
            previous = count;
        }
        // The last bucket is le="+Inf": every request, the same number as _count.
        assertThat(buckets.getLast()).contains("le=\"+Inf\"");
        String countLine = lines(text, "http_server_requests_seconds_count{").stream()
                .filter(l -> l.contains("uri=\"/orders/{id}\"") && l.contains("status=\"200\"")).findFirst().orElseThrow();
        assertThat(value(buckets.getLast())).isEqualTo(value(countLine)).isEqualTo(3.0);
    }

    @Test
    void theUriTagIsTheTemplate_notTheRealPath() {
        http.getForObject("/orders/1", String.class);
        http.getForObject("/orders/2", String.class);
        String server = String.join("\n", lines(scrape(), "http_server_requests_seconds_count{"));

        // One server series for /orders/{id}, not one per order id (topic 03: cardinality).
        assertThat(server).contains("uri=\"/orders/{id}\"").doesNotContain("uri=\"/orders/1\"");
        // The test's own HTTP client is measured too (http_client_requests). It was given a finished URL
        // string, not a template, so it tags every real path: one series per order id.
        assertThat(lines(scrape(), "http_client_requests_seconds_count{")).anyMatch(l -> l.contains("uri=\"/orders/1\""));
    }

    private String scrape() {
        return http.getForObject("/actuator/prometheus", String.class);
    }

    private static List<String> lines(String text, String prefix) {
        return Arrays.stream(text.split("\n")).filter(l -> l.startsWith(prefix)).toList();
    }

    private static String line(String text, String prefix) {
        return lines(text, prefix).getFirst();
    }

    private static double value(String line) {
        return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
    }
}
