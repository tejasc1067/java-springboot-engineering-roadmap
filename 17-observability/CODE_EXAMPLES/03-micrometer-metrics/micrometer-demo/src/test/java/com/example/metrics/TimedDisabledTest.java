package com.example.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** By default @Timed is only metadata: nothing reads it, so nothing is recorded. */
@SpringBootTest
class TimedDisabledTest {

    @Autowired InvoiceRenderer renderer;
    @Autowired MeterRegistry registry;

    @Test
    void withoutTheProperty_timedRecordsNothing() {
        renderer.render("order-1");

        assertThat(registry.find("invoice.render").timer()).isNull();
    }
}
