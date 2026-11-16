package com.example.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "management.observations.annotations.enabled=true")
class TimedEnabledTest {

    @Autowired InvoiceRenderer renderer;
    @Autowired MeterRegistry registry;

    @Test
    void withThePropertyAndAop_timedRecordsATimer() {
        renderer.render("order-1");
        renderer.render("order-2");

        assertThat(registry.get("invoice.render").timer().count()).isEqualTo(2);
    }
}
