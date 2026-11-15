package com.example;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) { this.orders = orders; }

    // In JPA this would be enough: the entity is managed, and dirty checking writes the change at commit.
    // Spring Data JDBC has no dirty checking. Without save(), nothing is written.
    @Transactional
    public void shipWithoutSave(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        order.setStatus("SHIPPED");
    }

    @Transactional
    public void ship(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        order.setStatus("SHIPPED");
        orders.save(order);
    }
}
