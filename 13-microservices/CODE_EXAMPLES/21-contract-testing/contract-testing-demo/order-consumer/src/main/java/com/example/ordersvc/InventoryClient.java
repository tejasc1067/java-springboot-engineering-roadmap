package com.example.ordersvc;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/**
 * order-service's declarative HTTP view of inventory-service (topic 05). On a non-2xx status it
 * throws HttpClientErrorException / HttpServerErrorException. This is the real client the
 * contract stub is run against.
 */
public interface InventoryClient {

    @GetExchange("/api/inventory/{sku}")
    StockResponse getStock(@PathVariable String sku);
}
