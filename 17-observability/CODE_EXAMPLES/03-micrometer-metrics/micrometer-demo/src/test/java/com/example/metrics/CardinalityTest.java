package com.example.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardinalityTest {

    @Test
    void aUserIdTag_createsOneTimeSeriesPerUser() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        OrderMetrics metrics = new OrderMetrics(registry);

        for (int user = 0; user < 10_000; user++) metrics.orderPlacedBroken("user-" + user);

        assertThat(registry.getMeters()).hasSize(10_000);
        assertThat(registry.scrape().lines().filter(l -> l.startsWith("orders_placed_total{"))).hasSize(10_000);
        // Every one stays in memory in the app and in Prometheus, and the next 10,000 users add 10,000 more.
    }

    @Test
    void aBoundedTag_keepsTheSeriesCountFixed_howeverManyUsersThereAre() {
        MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        OrderMetrics metrics = new OrderMetrics(registry);

        for (int user = 0; user < 10_000; user++) metrics.orderPlaced(user % 10 == 0 ? "pro" : "free");

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.get("orders.placed").tag("plan", "pro").counter().count()).isEqualTo(1_000);
    }

    @Test
    void aMeterFilter_capsTheDamage_whenSomeoneAddsAnUnboundedTag() {
        MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        // Register filters before any meter exists: they don't apply to meters already registered.
        registry.config().meterFilter(MeterFilter.maximumAllowableTags("orders.placed", "userId", 100, MeterFilter.deny()));
        OrderMetrics metrics = new OrderMetrics(registry);

        for (int user = 0; user < 10_000; user++) metrics.orderPlacedBroken("user-" + user);

        assertThat(registry.getMeters()).hasSize(100);
        double counted = registry.find("orders.placed").counters().stream().mapToDouble(c -> c.count()).sum();
        assertThat(counted).isEqualTo(100);   // orders from the other 9,900 users weren't counted at all
    }
}
