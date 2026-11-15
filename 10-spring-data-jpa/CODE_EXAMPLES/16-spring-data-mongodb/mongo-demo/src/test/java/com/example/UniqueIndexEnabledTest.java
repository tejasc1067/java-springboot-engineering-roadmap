package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// With auto-index-creation on, Spring Data creates the declared indexes at startup.
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
class UniqueIndexEnabledTest {

    @Autowired CustomerRepository customers;

    @Test
    void createdUniqueIndex_rejectsTheDuplicate() {
        customers.deleteAll();
        customers.save(new Customer("bob@example.com", "Bob"));

        assertThatThrownBy(() -> customers.save(new Customer("bob@example.com", "Bob again")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
