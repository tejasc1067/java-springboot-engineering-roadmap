package com.example;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class SchemaInCodeTest {

    @Autowired OrderRepository orders;
    @Autowired MongoTemplate mongo;

    @Test
    void documentsWrittenUnderAnOldFieldName_loadWithNulls() {
        orders.deleteAll();
        // Last month's version of the app called the field "clientId". Its documents are still there.
        mongo.insert(new Document("_id", "old-1").append("clientId", "c1").append("status", "NEW"), "orders");

        Order old = orders.findById("old-1").orElseThrow();

        // No error on read, no error on startup. The value is simply not where the code looks.
        assertThat(old.getCustomerId()).isNull();
        assertThat(orders.findByCustomerId("c1")).isEmpty();
    }
}
