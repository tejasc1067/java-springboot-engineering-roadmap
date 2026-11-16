package com.example.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private final ObservationRegistry registry;
    private final Tracer tracer;

    public StockService(ObservationRegistry registry, Tracer tracer) {
        this.registry = registry;
        this.tracer = tracer;
    }

    public int check(String sku) {
        // One Observation becomes a span (and a timer metric). It's a child of whatever span is current.
        return Observation.createNotStarted("stock.check", registry)
                .contextualName("check-stock")                  // the span's name
                .lowCardinalityKeyValue("warehouse", "eu")      // span attribute AND metric tag: few values only
                .highCardinalityKeyValue("sku", sku)            // span attribute only: any number of values is fine
                .observe(() -> lookUp(sku));
    }

    private int lookUp(String sku) {
        // An event is a timestamped note inside the current span.
        tracer.currentSpan().event("cache-miss");
        if (sku.startsWith("unknown")) {
            throw new IllegalArgumentException("no such sku: " + sku);
        }
        return 7;
    }
}
