package com.example.msgtest;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The business logic. It knows nothing about Kafka or RabbitMQ, so it's tested with plain JUnit.
 * An order event looks like "order-7:2500" (order id, amount in cents).
 */
@Service
public class ShippingService {

    private final Map<String, String> shipmentsByOrder = new ConcurrentHashMap<>();

    /** Returns the new shipment id, or empty if the event is invalid or the order already shipped. */
    public Optional<String> handle(String orderEvent) {
        String[] parts = orderEvent.split(":");
        if (parts.length != 2 || !parts[1].matches("\\d+") || Long.parseLong(parts[1]) <= 0) {
            return Optional.empty();
        }
        String orderId = parts[0];
        String shipmentId = "ship-" + orderId;
        // putIfAbsent: a redelivered event doesn't ship twice (topic 08)
        return shipmentsByOrder.putIfAbsent(orderId, shipmentId) == null ? Optional.of(shipmentId) : Optional.empty();
    }

    public Optional<String> shipmentFor(String orderId) {
        return Optional.ofNullable(shipmentsByOrder.get(orderId));
    }
}
