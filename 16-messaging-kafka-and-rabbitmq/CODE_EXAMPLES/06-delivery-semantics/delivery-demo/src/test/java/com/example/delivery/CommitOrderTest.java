package com.example.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * At-most-once vs at-least-once is decided by one thing: do you commit the offset before or after
 * you process the message? A "crash" here is closing the consumer between the two steps.
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {"orders-amo", "orders-alo"})
class CommitOrderTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    final List<String> charged = new ArrayList<>();

    @Test
    void commitThenProcess_crashInBetween_theMessageIsLost() throws Exception {
        send("orders-amo", "order-1");

        try (Consumer<String, String> consumer = subscribe("orders-amo")) {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), 1);
            consumer.commitSync();            // 1. "I've got it"
            // 2. crash before processing: charge(...) never runs
        }

        try (Consumer<String, String> restarted = subscribe("orders-amo")) {
            ConsumerRecords<String, String> again = restarted.poll(Duration.ofSeconds(2));
            again.forEach(this::charge);
            assertThat(again.count()).isZero();   // the group's offset says it was handled
        }
        assertThat(charged).isEmpty();            // at-most-once: order-1 was never charged
    }

    @Test
    void processThenCommit_crashInBetween_theMessageIsProcessedTwice() throws Exception {
        send("orders-alo", "order-2");

        try (Consumer<String, String> consumer = subscribe("orders-alo")) {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), 1).forEach(this::charge);   // 1. process
            // 2. crash before commitSync()
        }

        try (Consumer<String, String> restarted = subscribe("orders-alo")) {
            KafkaTestUtils.getRecords(restarted, Duration.ofSeconds(10), 1).forEach(this::charge);
            restarted.commitSync();
        }
        assertThat(charged).containsExactly("order-2", "order-2");   // at-least-once: charged twice
    }

    private void charge(ConsumerRecord<String, String> record) {
        charged.add(record.value());
    }

    private void send(String topic, String value) throws Exception {
        try (Producer<String, String> producer = Clients.producer(broker, Map.of())) {
            producer.send(new ProducerRecord<>(topic, value)).get();
        }
    }

    private Consumer<String, String> subscribe(String topic) {
        Consumer<String, String> consumer = Clients.consumer(broker, "billing-" + topic, Map.of());
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
