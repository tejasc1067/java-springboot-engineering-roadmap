package com.example.promql;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.example.promql.PrometheusEnvironment.FAKE;
import static com.example.promql.PrometheusEnvironment.awaitScraped;
import static com.example.promql.PrometheusEnvironment.single;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PercentileAveragingTest {

    private static final String[] LE = {"0.01", "0.1", "1", "2.5", "+Inf"};

    @Test
    void theAverageOfTwoP95s_isNotTheP95() {
        setPod("a", Map.of("0.01", 0.0, "0.1", 0.0, "1", 0.0, "2.5", 0.0, "+Inf", 0.0));
        setPod("b", Map.of("0.01", 0.0, "0.1", 0.0, "1", 0.0, "2.5", 0.0, "+Inf", 0.0));
        awaitScraped("sum(checkout_seconds_count)", v -> v == 0);

        // Pod a: 1000 requests, all within 10 ms. Pod b: 100 requests, 90 fast and 10 that took 2 s.
        setPod("a", Map.of("0.01", 1000.0, "0.1", 1000.0, "1", 1000.0, "2.5", 1000.0, "+Inf", 1000.0));
        setPod("b", Map.of("0.01", 90.0, "0.1", 90.0, "1", 90.0, "2.5", 100.0, "+Inf", 100.0));
        awaitScraped("sum(checkout_seconds_count)", v -> v == 1100);

        double p95PodA = single("histogram_quantile(0.95, sum by (le) (rate(checkout_seconds_bucket{pod=\"a\"}[5m])))");
        double p95PodB = single("histogram_quantile(0.95, sum by (le) (rate(checkout_seconds_bucket{pod=\"b\"}[5m])))");
        assertThat(p95PodA).isCloseTo(0.0095, within(0.0001));
        assertThat(p95PodB).isCloseTo(1.75, within(0.001));   // interpolated inside the (1 s, 2.5 s] bucket

        // Broken: average the per-pod p95s. "p95 is 880 ms!"
        double averaged = single("avg(histogram_quantile(0.95, sum by (pod, le) (rate(checkout_seconds_bucket[5m]))))");
        assertThat(averaged).isCloseTo(0.88, within(0.001));

        // Right: add the buckets first, then take one quantile. 10 slow requests out of 1100 is 0.9%,
        // so 95% of requests took under 10 ms.
        double p95 = single("histogram_quantile(0.95, sum by (le) (rate(checkout_seconds_bucket[5m])))");
        assertThat(p95).isCloseTo(0.0096, within(0.0001));
    }

    private static void setPod(String pod, Map<String, Double> cumulative) {
        for (String le : LE) {
            FAKE.set("checkout_seconds_bucket{pod=\"" + pod + "\",le=\"" + le + "\"}", cumulative.get(le));
        }
        FAKE.set("checkout_seconds_count{pod=\"" + pod + "\"}", cumulative.get("+Inf"));
    }
}
