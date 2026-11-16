package com.example.ordering;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What a consumer builds from the events: the latest status of each order.
 * It trusts the order the events arrive in, like most real consumers do.
 */
public class OrderStatusView {

    private final Map<String, OrderEvent.Type> status = new ConcurrentHashMap<>();
    private final Map<String, List<OrderEvent.Type>> received = new ConcurrentHashMap<>();

    public void apply(OrderEvent event) {
        received.computeIfAbsent(event.orderId(), id -> new CopyOnWriteArrayList<>()).add(event.type());
        status.put(event.orderId(), event.type());   // last event wins
    }

    public OrderEvent.Type statusOf(String orderId) {
        return status.get(orderId);
    }

    public List<OrderEvent.Type> receivedFor(String orderId) {
        return received.getOrDefault(orderId, List.of());
    }

    public int orderCount() {
        return received.size();
    }
}
