package com.example.signals;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Spring Boot hands every SpanExporter bean to the tracer; this one keeps spans in memory. */
@TestConfiguration
public class InMemorySpans {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    /** Spans are exported in batches on a background thread; flush so the test sees them now. */
    static List<SpanData> finished(SdkTracerProvider tracerProvider, InMemorySpanExporter exporter) {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return exporter.getFinishedSpanItems();
    }
}
