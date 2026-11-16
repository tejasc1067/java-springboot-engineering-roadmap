package com.example.msgtest;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Thin: receive, hand to the service, publish the result. All the logic is in ShippingService. */
@Component
@Profile("kafka")
public class KafkaOrderListener {

    private final ShippingService shipping;
    private final KafkaTemplate<String, String> kafka;
    private final AtomicInteger received = new AtomicInteger();

    public KafkaOrderListener(ShippingService shipping, KafkaTemplate<String, String> kafka) {
        this.shipping = shipping;
        this.kafka = kafka;
    }

    @KafkaListener(id = "shipping", topics = "orders", groupId = "shipping")
    public void onOrder(String orderEvent) {
        received.incrementAndGet();
        shipping.handle(orderEvent)
                .ifPresent(shipmentId -> kafka.send("shipments", orderEvent.split(":")[0], shipmentId));
    }

    /** How many events this listener has processed. Tests use it to prove the listener ran at all. */
    public int received() {
        return received.get();
    }
}
