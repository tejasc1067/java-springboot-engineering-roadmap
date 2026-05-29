package com.example;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private final ApplicationEventPublisher events;

    public OrderService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public String place(String customer) {
        String id = UUID.randomUUID().toString();
        // OrderService does not know AuditLogger or NotificationSender exist.
        events.publishEvent(new OrderPlacedEvent(id, customer));
        return id;
    }
}
