package com.example;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class NotificationSender {

    private final List<String> sent = new ArrayList<>();

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String line = "NOTIFY " + event.customer() + ": your order " + event.orderId() + " is confirmed";
        sent.add(line);
        System.out.println(line);
    }

    public List<String> sent() {
        return sent;
    }
}
