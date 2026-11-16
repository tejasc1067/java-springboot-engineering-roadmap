package com.example.promql;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashSet;
import java.util.Set;

import static com.example.promql.PrometheusEnvironment.APP_HTTP;
import static com.example.promql.PrometheusEnvironment.awaitScraped;
import static com.example.promql.PrometheusEnvironment.query;
import static com.example.promql.PrometheusEnvironment.single;
import static org.assertj.core.api.Assertions.assertThat;

/** A real Prometheus scraping the real app every second. */
class PromqlBasicsTest {

    private static final String OK_REQUESTS = "http_server_requests_seconds_count{uri=\"/orders/{id}\",status=\"200\"}";

    @Test
    void theRawCounter_isATotalSinceTheAppStarted_rateTurnsItIntoPerSecond() {
        get("/orders/1");
        awaitScraped(OK_REQUESTS, v -> v == 1);      // a first sample, so rate() has something to compare with
        for (int i = 0; i < 29; i++) get("/orders/1");
        awaitScraped(OK_REQUESTS, v -> v == 30);

        // Without rate(): "30 requests since the process started". Graphed, it's a line that only climbs.
        assertThat(single(OK_REQUESTS)).isEqualTo(30.0);

        // With rate(): requests per second, averaged over the last minute. This is what you graph and alert on.
        double perSecond = single("rate(" + OK_REQUESTS + "[1m])");
        assertThat(perSecond).isGreaterThan(0).isLessThan(30);

        // increase() = rate() x window. Prometheus extrapolates to the window's edges, so it's rarely a whole number.
        assertThat(single("increase(" + OK_REQUESTS + "[1m])")).isGreaterThanOrEqualTo(29);
    }

    @Test
    void sumBy_keepsTheLabelsYouName_andAddsUpTheRest() {
        get("/orders/2");
        try { get("/orders/missing-1"); } catch (HttpClientErrorException.NotFound expected) { }
        awaitScraped("sum(http_server_requests_seconds_count{uri=\"/orders/{id}\",status=\"404\"})", v -> v >= 1);

        // One series per status left; method, outcome, exception, instance... are summed away.
        JsonNode result = query("sum by (status) (http_server_requests_seconds_count{uri=\"/orders/{id}\"})")
                .path("data").path("result");
        Set<String> statuses = new HashSet<>();
        result.forEach(series -> {
            statuses.add(series.path("metric").path("status").asText());
            assertThat(series.path("metric").size()).isEqualTo(1);   // only "status" is left
        });
        assertThat(statuses).contains("200", "404");
    }

    @Test
    void instantVersusRangeVector() {
        get("/orders/3");
        awaitScraped("sum(" + OK_REQUESTS + ")", v -> v >= 1);

        // Instant vector: one value per series, "now".
        assertThat(query(OK_REQUESTS).path("data").path("resultType").asText()).isEqualTo("vector");
        // Range vector: every sample from the last 30 s per series. Functions like rate() take these.
        JsonNode range = query(OK_REQUESTS + "[30s]").path("data");
        assertThat(range.path("resultType").asText()).isEqualTo("matrix");
        assertThat(range.path("result").get(0).path("values").size()).isGreaterThan(1);
    }

    private static void get(String path) {
        APP_HTTP.get().uri(path).retrieve().body(String.class);
    }
}
