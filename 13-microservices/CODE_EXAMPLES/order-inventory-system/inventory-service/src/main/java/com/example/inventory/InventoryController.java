package com.example.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ProductRepository products;

    public InventoryController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockResponse> getStock(@PathVariable String sku) {
        return products.findById(sku)
                .map(p -> ResponseEntity.ok(new StockResponse(p.getSku(), p.getAvailableStock())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
