package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Finds inventory-service BY NAME through the registry instead of a hardcoded URL.
 * DiscoveryClient.getInstances("inventory-service") asks Eureka where inventory-service
 * currently lives; we call whichever instance it reports. Instances can be added,
 * removed, or moved and this code never changes — that's what discovery buys you.
 *
 * (This does the lookup explicitly to show the mechanism. In real code you'd let a
 * @LoadBalanced client / Spring Cloud LoadBalancer resolve the name and balance for you
 * — that balancing is what ../../loadbalancer-demo proves.)
 */
@SpringBootApplication
@RestController
public class OrderServiceApplication {

    private final DiscoveryClient discoveryClient;

    public OrderServiceApplication(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @GetMapping("/api/check/{sku}")
    public String check(@PathVariable String sku) {
        List<ServiceInstance> instances = discoveryClient.getInstances("inventory-service");
        if (instances.isEmpty()) {
            return "{\"error\":\"inventory-service not registered in discovery\"}";
        }
        ServiceInstance instance = instances.get(0); // discovery resolved the name to a live host:port
        return RestClient.create()
                .get()
                .uri(instance.getUri() + "/api/inventory/{sku}", sku)
                .retrieve()
                .body(String.class);
    }
}
