package com.example.kafkaconcepts;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Plain Kafka clients, configured the minimal way, so the tests show Kafka itself rather than Spring. */
final class KafkaClients {

    private KafkaClients() {
    }

    static KafkaProducer<String, String> producer(String bootstrapServers) {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    static KafkaConsumer<String, String> consumer(String bootstrapServers, String groupId) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",   // a new group starts at the beginning
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,      // the tests commit explicitly
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    static AdminClient admin(String bootstrapServers) {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    /** Polls until {@code count} records have arrived, or fails after 10 seconds. */
    static List<ConsumerRecord<String, String>> pollUntil(KafkaConsumer<String, String> consumer, int count) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (received.size() < count) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("expected " + count + " records, got " + received.size());
            }
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
        }
        return received;
    }
}
