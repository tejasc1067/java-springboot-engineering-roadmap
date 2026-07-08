package com.example.synccall;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/**
 * The declarative HTTP client: the remote inventory API described as a Java
 * interface. Spring generates the implementation that turns getStock("SKU-BOOK")
 * into GET /api/inventory/SKU-BOOK and parses the JSON body into StockResponse.
 */
public interface InventoryClient {

    @GetExchange("/api/inventory/{sku}")
    StockResponse getStock(@PathVariable String sku);
}
