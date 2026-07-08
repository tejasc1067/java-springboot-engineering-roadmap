package com.example.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a few products at startup so the demo has stock to query. In-memory H2 is
 * wiped on restart, so this runs every boot. Real inventory data would come from
 * this service's own persistent database — the point is that it lives HERE, owned
 * by inventory-service, and no other service touches these rows.
 */
@Component
public class InventoryDataSeeder implements CommandLineRunner {

    private final ProductRepository products;

    public InventoryDataSeeder(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(String... args) {
        products.save(new Product("SKU-BOOK", 5));
        products.save(new Product("SKU-PEN", 100));
        products.save(new Product("SKU-SOLDOUT", 0));
    }
}
