package com.example.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// No @AutoConfigureTestDatabase(replace = NONE) needed: since Boot 3.4, @DataJpaTest keeps a database
// that comes from the test itself (@ServiceConnection) instead of swapping in H2. Older tutorials still show it.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")   // Boot's default for a non-embedded DB is "none"
@Testcontainers
class PostgresProductRepositoryTest {

    // static: one container for every test in this class (started once, stopped after the last test).
    // @ServiceConnection: Boot sets spring.datasource.url/username/password from the running container.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductRepository products;

    @BeforeEach
    void seed() {
        products.save(new Product("Keyboard", 5));
        products.save(new Product("Old mouse", 0));
        products.save(new Product("New monitor", null));
    }

    @Test
    void sameBrokenQuery_failsOnRealPostgres() {
        assertThatThrownBy(() -> products.findInStockBroken())
                .isInstanceOf(InvalidDataAccessResourceUsageException.class)
                .rootCause().hasMessageContaining("function ifnull(integer, integer) does not exist");
    }

    @Test
    void portableQuery_passesOnRealPostgres() {
        assertThat(products.findInStock()).extracting(Product::getName).containsExactly("Keyboard");
    }
}
