package com.example;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

// One repository per aggregate ROOT. There is deliberately no OrderItemRepository.
public interface OrderRepository extends ListCrudRepository<Order, Long> {

    List<Order> findByStatus(String status);

    // Plain SQL - Spring Data JDBC has no JPQL. What you write is what runs.
    @Query("select o.* from purchase_order o join order_item i on i.order_id = o.id where i.sku = :sku")
    List<Order> findContainingSku(String sku);
}
