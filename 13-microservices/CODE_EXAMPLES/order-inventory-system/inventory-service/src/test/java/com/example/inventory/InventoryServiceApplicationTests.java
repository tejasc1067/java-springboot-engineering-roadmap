package com.example.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the service's own context boots: JPA maps Product against its own H2,
 * the seeder runs, and the repository is wired. No network, no other service.
 */
@SpringBootTest
class InventoryServiceApplicationTests {

    @Autowired
    ProductRepository products;

    @Test
    void seedsItsOwnInventory() {
        assertThat(products.findById("SKU-BOOK")).isPresent();
        assertThat(products.findById("SKU-SOLDOUT")).get()
                .extracting(Product::getAvailableStock).isEqualTo(0);
    }
}
