package com.example;

import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> byId = new HashMap<>();

    @Override
    public void save(Order order) {
        byId.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public int count() {
        return byId.size();
    }
}
