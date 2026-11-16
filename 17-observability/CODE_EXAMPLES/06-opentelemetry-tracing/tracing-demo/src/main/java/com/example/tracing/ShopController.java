package com.example.tracing;

import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class ShopController {

    private final Tracer tracer;
    private final StockService stock;
    private final PricingService pricing;
    private final ReportService reports;
    private final RestClient tracedClient;
    private final RestClient untracedClient;
    private final Environment env;

    public ShopController(Tracer tracer, StockService stock, PricingService pricing, ReportService reports,
                          @Qualifier("tracedClient") RestClient tracedClient,
                          @Qualifier("untracedClient") RestClient untracedClient, Environment env) {
        this.tracer = tracer;
        this.stock = stock;
        this.pricing = pricing;
        this.reports = reports;
        this.tracedClient = tracedClient;
        this.untracedClient = untracedClient;
        this.env = env;
    }

    // "Inventory service": reports the trace it sees and the traceparent header it received.
    @GetMapping("/stock/{sku}")
    Map<String, Object> stock(@PathVariable String sku,
                              @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return Map.of("sku", sku, "available", stock.check(sku),
                "traceId", traceId(), "traceparent", String.valueOf(traceparent));
    }

    @GetMapping("/price/{sku}")
    Map<String, Object> price(@PathVariable String sku) {
        return Map.of("sku", sku, "priceCents", pricing.price(sku), "traceId", traceId());
    }

    // "Order service": calls the stock endpoint over HTTP with the chosen client.
    @GetMapping("/checkout")
    Map<String, Object> checkout(@RequestParam String sku, @RequestParam(defaultValue = "traced") String client) {
        RestClient http = client.equals("traced") ? tracedClient : untracedClient;
        Map<?, ?> downstream = http.get()
                .uri("http://localhost:" + env.getProperty("local.server.port") + "/stock/" + sku)
                .retrieve()
                .body(Map.class);
        return Map.of("traceId", traceId(), "downstream", downstream);
    }

    @GetMapping("/report")
    Map<String, Object> report() throws Exception {
        return Map.of("traceId", traceId(), "asyncTraceId", reports.build().get());
    }

    private String traceId() {
        return tracer.currentSpan().context().traceId();
    }
}
