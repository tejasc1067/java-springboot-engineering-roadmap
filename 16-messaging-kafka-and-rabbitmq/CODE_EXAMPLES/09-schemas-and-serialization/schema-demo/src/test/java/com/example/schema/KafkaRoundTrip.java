package com.example.schema;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Sends one record through the embedded broker and reads it back, with the serializers under test. */
final class KafkaRoundTrip {

    private final EmbeddedKafkaBroker broker;

    KafkaRoundTrip(EmbeddedKafkaBroker broker) {
        this.broker = broker;
    }

    /** Sends {@code value}; each entry of {@code extraHeaders} is added as a record header. */
    <T> String send(Serializer<T> serializer, T value, Map<String, String> extraHeaders) throws Exception {
        String topic = "orders-" + UUID.randomUUID();
        ProducerRecord<String, T> record = new ProducerRecord<>(topic, "order-1", value);
        extraHeaders.forEach((k, v) -> record.headers().add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8))));
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        try (KafkaProducer<String, T> producer = new KafkaProducer<>(props, new StringSerializer(), serializer)) {
            producer.send(record).get();
        }
        return topic;
    }

    <T> String send(Serializer<T> serializer, T value) throws Exception {
        return send(serializer, value, Map.of());
    }

    /** Reads the one record on {@code topic}. A deserialization failure is thrown from here, as in a real consumer. */
    <T> ConsumerRecord<String, T> receive(String topic, Deserializer<T> deserializer) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("shipping-" + UUID.randomUUID(), "false", broker);
        try (KafkaConsumer<String, T> consumer = new KafkaConsumer<>(props, new StringDeserializer(), deserializer)) {
            consumer.subscribe(java.util.List.of(topic));
            return KafkaTestUtils.getSingleRecord(consumer, topic);
        }
    }
}
