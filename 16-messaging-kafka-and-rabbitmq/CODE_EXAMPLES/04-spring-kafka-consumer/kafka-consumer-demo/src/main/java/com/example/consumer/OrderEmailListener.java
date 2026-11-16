package com.example.consumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEmailListener {

    private final List<String> sent = new CopyOnWriteArrayList<>();

    // Three consumers in the "email" group, but "orders" has only two partitions: one consumer gets nothing.
    @KafkaListener(id = "email", topics = "orders", groupId = "email", concurrency = "3")
    public void onOrder(ConsumerRecord<String, String> record) {
        sent.add(record.value());
    }

    public List<String> sent() {
        return sent;
    }
}
