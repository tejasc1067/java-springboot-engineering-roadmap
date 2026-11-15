package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NaturalKeyTest {

    @Autowired ProductRepository products;
    @Autowired JdbcAggregateTemplate aggregates;

    @BeforeEach
    void clean() {
        products.deleteAll();
    }

    @Test
    void saveWithAnIdAlreadySet_runsAnUpdate_andSilentlySavesNothing() {
        // save() decides insert vs update by looking at the id: null = new, anything else = existing.
        // The SKU is set, so it runs UPDATE "PRODUCT" ... WHERE "SKU" = 'SKU-1'. That matches no row.
        products.save(new Product("SKU-1", "Notebook"));   // no exception

        assertThat(products.count()).isZero();             // and no product
    }

    @Test
    void explicitInsert_works() {
        aggregates.insert(new Product("SKU-1", "Notebook"));

        assertThat(products.findById("SKU-1")).isPresent();
    }
}
