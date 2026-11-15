package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(OrderService.class)
class LostUpdateTest {

    @Autowired OrderRepository orders;
    @Autowired OrderService service;

    private String orderId;

    @BeforeEach
    void oneOrder() {
        orders.deleteAll();
        orderId = orders.save(new Order("c1", new Address("Leeds", "LS1"))).getId();
    }

    @Test
    void twoReadModifyWriteSaves_lastOneWins() {
        // Two requests load the same order before either saves (written out step by step so it's deterministic).
        Order seenByA = orders.findById(orderId).orElseThrow();
        Order seenByB = orders.findById(orderId).orElseThrow();

        seenByA.addItem("BOOK-1", 1, 1500);
        orders.save(seenByA);
        seenByB.addItem("PEN-3", 1, 200);
        orders.save(seenByB);                 // replaces the whole document, BOOK-1 included

        assertThat(orders.findById(orderId).orElseThrow().getItems())
                .extracting(OrderItem::sku).containsExactly("PEN-3");
    }

    @Test
    void atomicPush_keepsBothItems() {
        service.addItemAtomically(orderId, new OrderItem("BOOK-1", 1, 1500));
        service.addItemAtomically(orderId, new OrderItem("PEN-3", 1, 200));

        assertThat(orders.findById(orderId).orElseThrow().getItems())
                .extracting(OrderItem::sku).containsExactly("BOOK-1", "PEN-3");
    }
}
