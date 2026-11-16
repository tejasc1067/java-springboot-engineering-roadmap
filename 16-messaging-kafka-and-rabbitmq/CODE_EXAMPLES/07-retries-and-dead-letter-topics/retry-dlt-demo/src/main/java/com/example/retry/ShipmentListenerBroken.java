package com.example.retry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The same listener with the JsonDeserializer used directly: a bad record fails inside poll(). */
@Component
@Profile("shipments-broken")
public class ShipmentListenerBroken {

    private final List<Shipment> received = new CopyOnWriteArrayList<>();

    @KafkaListener(topics = "shipments", groupId = "shipments", properties = {
            "value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
            "spring.json.value.default.type=com.example.retry.Shipment"})
    public void on(Shipment shipment) {
        received.add(shipment);
    }

    public List<Shipment> received() {
        return received;
    }
}
