package com.example.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * order-service: handles an inbound request and calls inventory-service. Its RestClient
 * carries the CorrelationIdPropagationInterceptor, so the correlation id assigned to the
 * inbound request is forwarded to inventory-service — the whole chain shares one id.
 */
@SpringBootApplication
@RestController
public class OrderServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceApplication.class);

    private final RestClient inventory;

    public OrderServiceApplication(@Value("${inventory.uri}") String inventoryUri) {
        this.inventory = RestClient.builder()
                .baseUrl(inventoryUri)
                .requestInterceptor(new CorrelationIdPropagationInterceptor())
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @GetMapping("/api/order/{sku}")
    public String order(@PathVariable String sku) {
        log.info("handling order for {}", sku); // this log line carries the correlation id (see pattern)
        return inventory.get().uri("/api/inventory/{sku}", sku).retrieve().body(String.class);
    }
}
