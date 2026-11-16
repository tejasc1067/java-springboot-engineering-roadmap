package com.example.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final Tracer tracer;

    public ReportService(Tracer tracer) {
        this.tracer = tracer;
    }

    // Runs on another thread. Returns the trace id that thread sees, or "none".
    @Async
    public CompletableFuture<String> build() {
        Span span = tracer.currentSpan();
        return CompletableFuture.completedFuture(span == null ? "none" : span.context().traceId());
    }
}
