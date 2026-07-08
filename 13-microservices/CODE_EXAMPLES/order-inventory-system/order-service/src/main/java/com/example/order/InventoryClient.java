package com.example.order;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/**
 * A declarative HTTP client (topic 05). We describe the remote call as a Java
 * interface; Spring generates the implementation that actually makes the HTTP
 * request to inventory-service. This is the modern replacement for hand-writing
 * RestTemplate/RestClient calls or using the older @FeignClient.
 *
 * Note what is NOT here yet: no timeout, no retry, no fallback. As written, if
 * inventory-service is slow this call blocks the caller's thread indefinitely
 * (fallacies 1 and 2, topic 02). Topics 09-11 add exactly those protections.
 */
public interface InventoryClient {

    @GetExchange("/api/inventory/{sku}")
    StockResponse getStock(@PathVariable String sku);
}
