package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(OrderService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NoDirtyCheckingTest {

    @Autowired OrderRepository orders;
    @Autowired CustomerRepository customers;
    @Autowired OrderService service;

    private Long orderId;

    @BeforeEach
    void oneOrder() {
        orders.deleteAll();
        customers.deleteAll();
        Long customerId = customers.save(new Customer("Ann")).getId();
        orderId = orders.save(new Order(AggregateReference.to(customerId))).getId();
    }

    @Test
    void changingALoadedObject_withoutSave_writesNothing() {
        service.shipWithoutSave(orderId);

        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo("NEW");
    }

    @Test
    void explicitSave_writesTheChange() {
        service.ship(orderId);

        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo("SHIPPED");
    }
}
