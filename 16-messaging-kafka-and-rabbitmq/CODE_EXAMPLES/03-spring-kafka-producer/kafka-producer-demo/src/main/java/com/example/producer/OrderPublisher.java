package com.example.producer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class OrderPublisher {

    public static final String TOPIC = "orders";

    private final KafkaTemplate<String, Object> kafka;

    public OrderPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    /** Broken: send() only queues the record. Ignoring its future means ignoring whether it was ever written. */
    public void publishAndForget(OrderPlaced event) {
        kafka.send(TOPIC, event.customerId(), event);
    }

    /** Fixed: wait for the broker's answer, so a failure reaches the caller (and its transaction, retry, or 500). */
    public SendResult<String, Object> publish(OrderPlaced event) throws Exception {
        return kafka.send(TOPIC, event.customerId(), event).get(10, TimeUnit.SECONDS);
    }

    /** Fixed without blocking: hand the future back and let the caller decide what a failure means. */
    public CompletableFuture<SendResult<String, Object>> publishAsync(OrderPlaced event) {
        return kafka.send(TOPIC, event.customerId(), event);
    }
}
