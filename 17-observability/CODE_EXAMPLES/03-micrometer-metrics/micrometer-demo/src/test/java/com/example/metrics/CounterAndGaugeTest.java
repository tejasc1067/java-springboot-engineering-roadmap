package com.example.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

class CounterAndGaugeTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void aCounterOnlyGoesUp() {
        Counter placed = Counter.builder("orders.placed").description("Orders placed").register(registry);
        placed.increment();
        placed.increment(2);

        assertThat(placed.count()).isEqualTo(3);
        // There is no decrement(). Rates ("orders per second") are computed from the growing total later.
    }

    @Test
    void registeringTheSameNameAndTagsAgain_returnsTheSameCounter() {
        Counter.builder("orders.placed").register(registry).increment();
        Counter.builder("orders.placed").register(registry).increment();

        assertThat(registry.get("orders.placed").counter().count()).isEqualTo(2);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void aGaugeReadsTheCurrentValueWhenSampled() {
        ConcurrentLinkedQueue<String> emailQueue = new ConcurrentLinkedQueue<>();
        Gauge.builder("email.queue.size", emailQueue, ConcurrentLinkedQueue::size).register(registry);

        emailQueue.add("a");
        emailQueue.add("b");
        assertThat(registry.get("email.queue.size").gauge().value()).isEqualTo(2);

        emailQueue.poll();
        assertThat(registry.get("email.queue.size").gauge().value()).isEqualTo(1);   // gauges go up and down
    }

    @Test
    void aGaugeOnAnObjectNobodyKeeps_turnsIntoNaN() {
        // Micrometer holds the gauged object through a WeakReference, so this list can be garbage collected.
        registry.gauge("pending.orders", new ArrayList<>(List.of("o-1", "o-2", "o-3")), List::size);
        Gauge pending = registry.get("pending.orders").gauge();

        for (int i = 0; i < 50 && !Double.isNaN(pending.value()); i++) {
            System.gc();
        }
        assertThat(pending.value()).isNaN();
    }

    @Test
    void aStrongReferenceGauge_keepsReporting() {
        Gauge pending = Gauge.builder("pending.orders", new ArrayList<>(List.of("o-1", "o-2", "o-3")), List::size)
                .strongReference(true)
                .register(registry);

        for (int i = 0; i < 50; i++) System.gc();
        assertThat(pending.value()).isEqualTo(3);
    }
}
