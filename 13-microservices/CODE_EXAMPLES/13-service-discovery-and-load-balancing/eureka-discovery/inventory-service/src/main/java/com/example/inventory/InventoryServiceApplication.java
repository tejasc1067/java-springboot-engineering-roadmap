package com.example.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Eureka client: on startup it registers itself as "inventory-service" with the
 * registry (no code needed — the eureka-client starter + config do it). It also reports
 * which port answered, so when you run two instances you can see load balancing pick
 * between them.
 */
@SpringBootApplication
@RestController
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @GetMapping("/api/inventory/{sku}")
    public String getStock(@PathVariable String sku,
                           @org.springframework.beans.factory.annotation.Value("${server.port}") int port) {
        return "{\"sku\":\"" + sku + "\",\"available\":5,\"servedByPort\":" + port + "}";
    }
}
