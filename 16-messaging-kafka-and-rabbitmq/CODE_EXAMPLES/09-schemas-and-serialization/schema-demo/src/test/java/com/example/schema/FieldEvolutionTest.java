package com.example.schema;

import com.example.schema.orderservice.OrderPlacedV2;
import com.example.schema.orderservice.OrderPlacedWithCurrency;
import com.example.schema.shipping.OrderPlaced;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** order-service ships a new version of the event; shipping-service still runs the old code. */
@EmbeddedKafka
class FieldEvolutionTest {

    private KafkaRoundTrip kafka;

    @BeforeEach
    void setUp(EmbeddedKafkaBroker broker) {
        kafka = new KafkaRoundTrip(broker);
    }

    private static JsonDeserializer<OrderPlaced> oldShippingConsumer() {
        return new JsonDeserializer<>(OrderPlaced.class, false);
    }

    @Test
    void addingAField_theOldConsumerIgnoresIt() throws Exception {
        String topic = kafka.send(new JsonSerializer<>(), new OrderPlacedWithCurrency("o-1", 1999, "EUR"));

        assertThat(kafka.receive(topic, oldShippingConsumer()).value()).isEqualTo(new OrderPlaced("o-1", 1999));
    }

    @Test
    void renamingAField_theOldConsumerSilentlyReadsZero() throws Exception {
        String topic = kafka.send(new JsonSerializer<>(), new OrderPlacedV2("o-1", 1999));   // {"orderId":"o-1","totalCents":1999}

        // No exception: amountCents is missing (a primitive long becomes 0) and totalCents is an unknown field (ignored).
        assertThat(kafka.receive(topic, oldShippingConsumer()).value()).isEqualTo(new OrderPlaced("o-1", 0));
    }

    @Test
    void consumerWithAPlainObjectMapper_breaksOnAnyNewField() throws Exception {
        String topic = kafka.send(new JsonSerializer<>(), new OrderPlacedWithCurrency("o-1", 1999, "EUR"));

        // new ObjectMapper() keeps Jackson's default FAIL_ON_UNKNOWN_PROPERTIES = true.
        JsonDeserializer<OrderPlaced> strict = new JsonDeserializer<>(OrderPlaced.class, new ObjectMapper(), false);

        assertThatThrownBy(() -> kafka.receive(topic, strict))
                .hasStackTraceContaining("Unrecognized field \"currency\"");
    }
}
