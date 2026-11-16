package com.example.schema;

import com.example.schema.orderservice.OrderPlaced;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring Kafka's JsonSerializer writes the producer's Java class name into a "__TypeId__" header, and a
 * JsonDeserializer that follows the header needs that exact class. The consumer is another service with its own classes.
 */
@EmbeddedKafka
class TypeHeaderTest {

    private KafkaRoundTrip kafka;

    @BeforeEach
    void setUp(EmbeddedKafkaBroker broker) {
        kafka = new KafkaRoundTrip(broker);
    }

    @Test
    void jsonSerializer_putsTheProducersClassNameInAHeader() throws Exception {
        String topic = kafka.send(new JsonSerializer<>(), new OrderPlaced("o-1", 1999));

        ConsumerRecord<String, byte[]> record = kafka.receive(topic, new ByteArrayDeserializer());

        String typeId = new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8);
        assertThat(typeId).isEqualTo("com.example.schema.orderservice.OrderPlaced");
        assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo("{\"orderId\":\"o-1\",\"amountCents\":1999}");
    }

    @Test
    void defaultDeserializer_refusesAClassOutsideItsTrustedPackages() throws Exception {
        String topic = kafka.send(new JsonSerializer<>(), new OrderPlaced("o-1", 1999));

        assertThatThrownBy(() -> kafka.receive(topic, new JsonDeserializer<>()))
                .hasStackTraceContaining("is not in the trusted packages");
    }

    @Test
    void producerMovesItsClass_everyRecordFailsOnTheConsumer() throws Exception {
        // What order-service's JsonSerializer writes after its team moves OrderPlaced to an "events" package.
        // The consumer (trusting every package, the usual "fix" for the previous test) has no such class.
        JsonSerializer<OrderPlaced> movedClass = new JsonSerializer<>();
        movedClass.setAddTypeInfo(false);
        String topic = kafka.send(movedClass, new OrderPlaced("o-1", 1999),
                Map.of("__TypeId__", "com.example.schema.orderservice.events.OrderPlaced"));

        JsonDeserializer<Object> trustsEverything = new JsonDeserializer<>();
        trustsEverything.addTrustedPackages("*");

        assertThatThrownBy(() -> kafka.receive(topic, trustsEverything))
                .hasStackTraceContaining("ClassNotFoundException: com.example.schema.orderservice.events.OrderPlaced");
    }

    @Test
    void fixedTargetType_ignoresTheHeader_andReadsIntoTheConsumersOwnClass() throws Exception {
        String topic = kafka.send(new JsonSerializer<>(), new OrderPlaced("o-1", 1999),
                Map.of("__TypeId__", "com.example.schema.orderservice.events.OrderPlaced"));

        JsonDeserializer<com.example.schema.shipping.OrderPlaced> deserializer =
                new JsonDeserializer<>(com.example.schema.shipping.OrderPlaced.class, false);   // false: don't use type headers

        assertThat(kafka.receive(topic, deserializer).value())
                .isEqualTo(new com.example.schema.shipping.OrderPlaced("o-1", 1999));
    }

    @Test
    void typeMapping_sendsALogicalName_thatEachSideMapsToItsOwnClass() throws Exception {
        JsonSerializer<OrderPlaced> serializer = new JsonSerializer<>();
        serializer.configure(Map.of(JsonSerializer.TYPE_MAPPINGS,
                "orderPlaced:com.example.schema.orderservice.OrderPlaced"), false);
        String topic = kafka.send(serializer, new OrderPlaced("o-1", 1999));

        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.configure(Map.of(
                JsonDeserializer.TYPE_MAPPINGS, "orderPlaced:com.example.schema.shipping.OrderPlaced",
                JsonDeserializer.TRUSTED_PACKAGES, "com.example.schema.shipping"), false);

        ConsumerRecord<String, byte[]> raw = kafka.receive(topic, new ByteArrayDeserializer());
        assertThat(new String(raw.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8)).isEqualTo("orderPlaced");

        assertThat(kafka.receive(topic, deserializer).value())
                .isEqualTo(new com.example.schema.shipping.OrderPlaced("o-1", 1999));
    }
}
