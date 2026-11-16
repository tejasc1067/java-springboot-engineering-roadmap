package com.example.tracing;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// Test helper: spans are exported in batches every 5 s, so flush before reading them.
@Component
class Spans {

    private final SdkTracerProvider tracerProvider;
    private final InMemorySpanExporter exporter;

    Spans(SdkTracerProvider tracerProvider, InMemorySpanExporter exporter) {
        this.tracerProvider = tracerProvider;
        this.exporter = exporter;
    }

    List<SpanData> finished() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return exporter.getFinishedSpanItems();
    }

    SpanData named(String name) {
        return finished().stream().filter(s -> s.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no span named " + name + " in " + names()));
    }

    List<String> names() {
        return finished().stream().map(SpanData::getName).toList();
    }

    void clear() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        exporter.reset();
    }
}
