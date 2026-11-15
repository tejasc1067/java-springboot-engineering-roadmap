package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import static org.assertj.core.api.Assertions.assertThat;

// Default settings: @Indexed(unique = true) is on Customer.email, but the index is never created.
@DataMongoTest
class UniqueIndexTest {

    @Autowired CustomerRepository customers;

    @Test
    void uniqueAnnotationAlone_doesNotStopDuplicates() {
        customers.deleteAll();
        customers.save(new Customer("ann@example.com", "Ann"));
        customers.save(new Customer("ann@example.com", "Ann again"));

        assertThat(customers.findByEmail("ann@example.com")).hasSize(2);
    }
}
