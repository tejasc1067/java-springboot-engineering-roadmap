package com.example.synccall;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The same remote call as InventoryClient, written the Spring Cloud OpenFeign way.
 * Note the annotations: Feign reuses Spring MVC's @GetMapping (a server-side
 * annotation) on a client interface. @HttpExchange has its own client annotations.
 */
@FeignClient(name = "inventory", url = "${inventory.base-url}")
public interface FeignInventoryClient {

    @GetMapping("/api/inventory/{sku}")
    StockResponse getStock(@PathVariable("sku") String sku);
}
