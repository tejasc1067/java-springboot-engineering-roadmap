package com.example.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The real provider behaviour the contracts describe: a known SKU returns its stock, an unknown
 * one is a 404. The generated contract tests exercise exactly this controller.
 */
@RestController
public class InventoryController {

    @GetMapping("/api/inventory/{sku}")
    public StockDto getStock(@PathVariable String sku) {
        if ("SKU-BOOK".equals(sku)) {
            return new StockDto(sku, 5);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
