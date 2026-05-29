package com.example;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
