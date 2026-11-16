package com.example.promql;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static com.example.promql.PrometheusEnvironment.FAKE;
import static com.example.promql.PrometheusEnvironment.awaitScraped;
import static com.example.promql.PrometheusEnvironment.query;
import static com.example.promql.PrometheusEnvironment.single;
import static org.assertj.core.api.Assertions.assertThat;

class CounterResetTest {

    private static final String JOBS = "jobs_processed_total";

    @Test
    void anAppRestart_resetsTheCounter_rateAndIncreaseHandleIt_subtractionDoesNot() {
        // 100 -> 200 -> 300 jobs, then the app restarts and counts again from 0: 10 -> 20.
        for (double value : new double[] {100, 200, 300, 10, 20}) {
            FAKE.set(JOBS, value);
            awaitScraped(JOBS, v -> v == value);
        }
        // Really processed: 100 + 100 + 10 (the 10 after the restart) + 10 = 220.

        JsonNode samples = query(JOBS + "[5m]").path("data").path("result").get(0).path("values");
        double first = samples.get(0).get(1).asDouble();
        double last = samples.get(samples.size() - 1).get(1).asDouble();
        // Broken: "last value minus first value" says the app processed -80 jobs.
        assertThat(last - first).isEqualTo(-80);

        // rate()/increase() treat any drop as a reset and count the new value as growth from 0.
        assertThat(single("resets(" + JOBS + "[5m])")).isEqualTo(1);
        assertThat(single("increase(" + JOBS + "[5m])")).isGreaterThanOrEqualTo(220);
    }
}
