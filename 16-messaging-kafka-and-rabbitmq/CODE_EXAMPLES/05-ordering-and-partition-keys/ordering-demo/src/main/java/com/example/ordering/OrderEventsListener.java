package com.example.ordering;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private final OrderStatusView view = new OrderStatusView();

    // Three consumer threads in one group: each gets one of the topic's three partitions.
    @KafkaListener(topics = "order-events", groupId = "order-status", concurrency = "3")
    public void on(String value) {
        view.apply(OrderEvent.parse(value));
    }

    public OrderStatusView view() {
        return view;
    }
}
