package com.example.promql;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class OrderController {

    private final Counter ordersPlaced;
    private final AtomicInteger pendingOrders = new AtomicInteger();

    public OrderController(MeterRegistry registry) {
        // counter: only goes up. Exposed as orders_placed_total
        this.ordersPlaced = Counter.builder("orders.placed").register(registry);
        // gauge: a value that goes up and down. Exposed as orders_pending
        registry.gauge("orders.pending", pendingOrders);
    }

    @PostMapping("/orders")
    public String place() {
        ordersPlaced.increment();
        pendingOrders.incrementAndGet();
        return "placed";
    }

    @PostMapping("/orders/ship")
    public String ship() {
        pendingOrders.decrementAndGet();
        return "shipped";
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<String> find(@PathVariable String id) {
        return id.startsWith("missing") ? ResponseEntity.notFound().build() : ResponseEntity.ok("order " + id);
    }
}
