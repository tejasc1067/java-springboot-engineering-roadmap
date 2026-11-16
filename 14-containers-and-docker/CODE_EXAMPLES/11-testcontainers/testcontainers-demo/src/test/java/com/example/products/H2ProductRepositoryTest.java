package com.example.products;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest swaps the configured database for an embedded one (H2 here). Fast, no Docker - and not PostgreSQL.
@DataJpaTest
class H2ProductRepositoryTest {

    @Autowired ProductRepository products;

    @Test
    void brokenQuery_passesOnH2() {
        products.save(new Product("Keyboard", 5));
        products.save(new Product("Old mouse", 0));
        products.save(new Product("New monitor", null));

        assertThat(products.findInStockBroken()).extracting(Product::getName).containsExactly("Keyboard");
    }
}
