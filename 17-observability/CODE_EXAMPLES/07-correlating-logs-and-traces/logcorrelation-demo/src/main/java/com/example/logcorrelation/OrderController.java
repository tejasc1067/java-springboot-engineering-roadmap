package com.example.logcorrelation;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final Tracer tracer;
    private final ThreadPoolTaskExecutor plainExecutor;
    private final ThreadPoolTaskExecutor tracedExecutor;

    public OrderController(Tracer tracer,
                           @Qualifier("plainExecutor") ThreadPoolTaskExecutor plainExecutor,
                           @Qualifier("tracedExecutor") ThreadPoolTaskExecutor tracedExecutor) {
        this.tracer = tracer;
        this.plainExecutor = plainExecutor;
        this.tracedExecutor = tracedExecutor;
    }

    @GetMapping("/orders/{id}")
    Map<String, Object> order(@PathVariable String id) throws Exception {
        MDC.put("orderId", id);   // our own MDC key: only on this thread
        // Baggage travels with the trace context (other threads, and other services if it's a remote field),
        // and management.tracing.baggage.correlation.fields copies it into the MDC.
        try (BaggageInScope customer = tracer.createBaggageInScope("customerId", "c-" + id)) {
            log.info("loading order {}", id);
            if (id.startsWith("missing")) {
                throw new OrderNotFoundException(id);
            }
            if (id.startsWith("crash")) {
                throw new IllegalStateException("database unavailable");
            }
            CompletableFuture.runAsync(() -> log.info("emailing receipt (plain pool)"), plainExecutor).get();
            CompletableFuture.runAsync(() -> log.info("emailing receipt (traced pool)"), tracedExecutor).get();
            return Map.of("id", id, "status", "SHIPPED");
        } finally {
            MDC.remove("orderId");
        }
    }
}
