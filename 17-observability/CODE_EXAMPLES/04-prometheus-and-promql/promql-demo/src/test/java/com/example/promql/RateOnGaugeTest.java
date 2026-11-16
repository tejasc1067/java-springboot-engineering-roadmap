package com.example.promql;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static com.example.promql.PrometheusEnvironment.FAKE;
import static com.example.promql.PrometheusEnvironment.awaitScraped;
import static com.example.promql.PrometheusEnvironment.query;
import static com.example.promql.PrometheusEnvironment.single;
import static org.assertj.core.api.Assertions.assertThat;

class RateOnGaugeTest {

    private static final String QUEUE = "queue_depth";

    @Test
    void rateOnAGauge_readsEveryDropAsACounterReset_andInventsGrowth() {
        // A queue: 10 waiting, then 50, then 20. Net change: +10.
        for (double value : new double[] {10, 50, 20}) {
            FAKE.set(QUEUE, value);
            awaitScraped(QUEUE, v -> v == value);
        }

        // Broken: increase() thinks 50 -> 20 was a restart, so it counts 40 + 20 = 60 "new" items.
        JsonNode response = query("increase(" + QUEUE + "[5m])");
        double increase = response.path("data").path("result").get(0).path("value").get(1).asDouble();
        assertThat(increase).isGreaterThanOrEqualTo(60);
        // Prometheus 3 even says so, in the response's "infos" (Grafana shows it as a warning).
        assertThat(response.path("infos").toString()).contains("metric might not be a counter, name does not end in _total/_sum/_count/_bucket");

        // Gauges are read as they are, or with the *_over_time and delta/deriv functions.
        assertThat(single(QUEUE)).isEqualTo(20);
        assertThat(single("max_over_time(" + QUEUE + "[5m])")).isEqualTo(50);
        assertThat(single("delta(" + QUEUE + "[5m])")).isLessThan(30);   // about +10, extrapolated
    }
}
