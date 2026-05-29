package com.example;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuditLogger {

    private final List<String> entries = new ArrayList<>();

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String line = "AUDIT order=" + event.orderId() + " customer=" + event.customer();
        entries.add(line);
        System.out.println(line);
    }

    public List<String> entries() {
        return entries;
    }
}
