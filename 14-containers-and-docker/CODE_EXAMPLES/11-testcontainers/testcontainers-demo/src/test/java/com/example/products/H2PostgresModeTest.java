package com.example.products;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// "Just put H2 in PostgreSQL compatibility mode." It changes some behaviour - but it's still H2,
// with H2's functions. The broken query passes here too.
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:pgmode;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
// Without replace = NONE, @DataJpaTest would quietly swap this URL for a plain H2 database (the MODE check fails).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class H2PostgresModeTest {

    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;

    @Test
    void brokenQuery_stillPassesInH2PostgresMode() {
        // Prove this really is H2 in PostgreSQL mode, not a plain H2 swapped in behind our back.
        assertThat(jdbc.queryForObject(
                "select setting_value from information_schema.settings where setting_name = 'MODE'", String.class))
                .isEqualTo("PostgreSQL");

        products.save(new Product("Keyboard", 5));
        products.save(new Product("New monitor", null));

        assertThat(products.findInStockBroken()).extracting(Product::getName).containsExactly("Keyboard");
    }
}
