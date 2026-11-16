package com.example.retry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * JSON shipments. ErrorHandlingDeserializer wraps the JsonDeserializer: when the bytes aren't valid JSON
 * it doesn't throw inside poll(), it hands the failure to the error handler with the record attached.
 */
@Component
@Profile("shipments")
public class ShipmentListener {

    private final List<Shipment> received = new CopyOnWriteArrayList<>();

    @KafkaListener(topics = "shipments", groupId = "shipments", properties = {
            "value.deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
            "spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
            "spring.json.value.default.type=com.example.retry.Shipment"})
    public void on(Shipment shipment) {
        received.add(shipment);
    }

    public List<Shipment> received() {
        return received;
    }
}
