package com.example.signals;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Spring Boot's default sampling probability: metrics count every request, traces keep about 1 in 10. */
@SpringBootTest(properties = {
        "management.tracing.sampling.probability=0.1",
        "logging.level.com.example.signals=WARN"})
@Import(InMemorySpans.class)
class SamplingTest {

    @Autowired CheckoutService checkout;
    @Autowired MeterRegistry meters;
    @Autowired InMemorySpanExporter spans;
    @Autowired SdkTracerProvider tracerProvider;

    @Test
    void metricsCountEveryRequest_tracesKeepOnlyASample() {
        for (int i = 0; i < 200; i++) checkout.checkout("order-" + i, "sku-1");

        long tracedCheckouts = InMemorySpans.finished(tracerProvider, spans).stream()
                .filter(s -> s.getName().equals("checkout")).count();

        System.out.println("traced checkouts: " + tracedCheckouts + " of 200");
        assertThat(meters.get("checkout").timer().count()).isEqualTo(200);
        // About 20 expected. The bounds are wide so the test never flakes (sampling is random per trace).
        assertThat(tracedCheckouts).isBetween(1L, 60L);
    }
}
