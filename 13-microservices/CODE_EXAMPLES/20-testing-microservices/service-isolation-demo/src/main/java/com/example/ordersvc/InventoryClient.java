package com.example.ordersvc;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/**
 * order-service's view of the remote inventory-service, as a declarative HTTP interface
 * (topic 05). Spring turns getStock("SKU-BOOK") into GET /api/inventory/SKU-BOOK and, on a
 * non-2xx status, throws HttpClientErrorException / HttpServerErrorException.
 *
 * This is real network code — a wrong path, a bad JSON mapping, or mishandled status all live
 * here. A test that MOCKS this interface never runs any of it; a test that STUBS the wire does.
 */
public interface InventoryClient {

    @GetExchange("/api/inventory/{sku}")
    StockResponse getStock(@PathVariable String sku);
}
