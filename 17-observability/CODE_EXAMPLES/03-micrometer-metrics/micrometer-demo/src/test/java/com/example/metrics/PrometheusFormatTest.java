package com.example.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** What a scrape of /actuator/prometheus would contain for each kind of meter. */
class PrometheusFormatTest {

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    @Test
    void namesUseDotsInJava_andBecomeSnakeCaseWithUnitsInPrometheus() {
        Counter.builder("orders.placed").register(registry).increment();
        Timer.builder("checkout").register(registry).record(Duration.ofMillis(250));
        DistributionSummary.builder("http.request.body.size").baseUnit("bytes").register(registry).record(512);

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("orders_placed_total 1.0")
                .contains("checkout_seconds_count 1")
                .contains("checkout_seconds_sum 0.25")      // recorded in ms, exported in the base unit: seconds
                .contains("checkout_seconds_max 0.25")
                .contains("http_request_body_size_bytes_sum 512.0");
    }

    @Test
    void clientSidePercentiles_areComputedPerInstance_andCannotBeCombined() {
        Timer checkout = Timer.builder("checkout").publishPercentiles(0.95).register(registry);
        for (int ms = 1; ms <= 100; ms++) checkout.record(Duration.ofMillis(ms));

        assertThat(registry.scrape()).contains("checkout_seconds{quantile=\"0.95\"}");
        // One ready-made number per instance. The p95 of three instances is not the average of three p95s.
    }

    @Test
    void aPercentileHistogram_exportsBuckets_thatPrometheusCanCombine_atTheCostOfManySeries() {
        Timer checkout = Timer.builder("checkout").publishPercentileHistogram().register(registry);
        for (int ms = 1; ms <= 100; ms++) checkout.record(Duration.ofMillis(ms));

        long buckets = registry.scrape().lines().filter(l -> l.startsWith("checkout_seconds_bucket{")).count();
        System.out.println("histogram buckets for one timer: " + buckets);
        assertThat(buckets).isGreaterThan(50);   // each bucket is a time series, multiplied by every tag combination
    }
}
