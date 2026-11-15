package com.example;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class OrderRepositoryTest {

    @Autowired OrderRepository orders;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void clean() {
        orders.deleteAll();
    }

    @Test
    void savedOrder_isOneDocumentWithItemsInside() {
        Order saved = orders.save(new Order("c1", new Address("Leeds", "LS1 4AP"))
                .addItem("BOOK-1", 2, 1500)
                .addItem("PEN-3", 1, 200));

        // Read the raw stored document, bypassing the mapping, to see what MongoDB actually holds.
        Document raw = mongo.findById(saved.getId(), Document.class, "orders");

        assertThat(saved.getId()).isNotNull();                               // generated ObjectId
        assertThat(raw.get("shippingAddress", Document.class).getString("city")).isEqualTo("Leeds");
        assertThat(raw.getList("items", Document.class)).hasSize(2);       // items live inside the order
    }

    @Test
    void derivedQueries_reachIntoEmbeddedFields() {
        orders.save(new Order("c1", new Address("Leeds", "LS1")).addItem("BOOK-1", 1, 1500));
        orders.save(new Order("c1", new Address("York", "YO1")).addItem("PEN-3", 5, 200));
        Order shipped = new Order("c2", new Address("Leeds", "LS2")).addItem("BOOK-1", 3, 1500);
        shipped.setStatus("SHIPPED");
        orders.save(shipped);

        assertThat(orders.findByCustomerId("c1")).hasSize(2);
        assertThat(orders.findByItemsSku("BOOK-1")).hasSize(2);
        assertThat(orders.findByShippingAddressCityAndStatus("Leeds", "NEW")).hasSize(1);

        List<Order> bulk = orders.findWithAnyItemQuantityAtLeast(3);
        assertThat(bulk).extracting(Order::getCustomerId).containsExactlyInAnyOrder("c1", "c2");
    }
}
