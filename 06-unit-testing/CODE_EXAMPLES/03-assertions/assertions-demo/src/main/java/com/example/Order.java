package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {

    private final List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public long totalCents() {
        return items.stream()
                .mapToLong(OrderItem::lineTotalCents)
                .sum();
    }
}
