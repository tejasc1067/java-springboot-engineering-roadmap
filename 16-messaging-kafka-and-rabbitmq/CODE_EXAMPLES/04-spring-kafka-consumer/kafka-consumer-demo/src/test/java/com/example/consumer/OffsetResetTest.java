package com.example.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class OffsetResetTest extends KafkaTestBase {

    @Autowired
    ConsumerFactory<String, String> consumers;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Test
    void aNewGroupWithTheDefaultLatest_missesEverythingPublishedBeforeItJoined() throws Exception {
        for (int i = 1; i <= 3; i++) {
            kafka.send("invoices", "invoice-" + i).get();   // published while the new service didn't exist yet
        }

        // No auto.offset.reset set anywhere: Kafka's default, "latest", applies.
        try (Consumer<String, String> billing = consumers.createConsumer("billing", null)) {
            joinAndFixPosition(billing, "invoices");
            kafka.send("invoices", "invoice-4").get();

            assertThat(values(pollUntil(billing, 1))).containsExactly("invoice-4");   // 1, 2, 3 are skipped
        }

        // The same records, read by a group that starts from the earliest offset.
        try (Consumer<String, String> audit = consumers.createConsumer("audit", null, null,
                propsOf(Map.of(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")))) {
            audit.subscribe(List.of("invoices"));
            assertThat(values(pollUntil(audit, 4)))
                    .containsExactlyInAnyOrder("invoice-1", "invoice-2", "invoice-3", "invoice-4");
        }
    }

    /** Waits until the group has its partitions and has decided where to start reading. */
    static void joinAndFixPosition(Consumer<String, String> consumer, String topic) {
        consumer.subscribe(List.of(topic));
        long deadline = System.currentTimeMillis() + 30_000;
        while (consumer.assignment().size() < 2 && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }
        consumer.assignment().forEach(consumer::position);   // forces the start position (committed, or the reset) now
    }

    static List<ConsumerRecord<String, String>> pollUntil(Consumer<String, String> consumer, int count) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (received.size() < count && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
        }
        // One more short poll, to catch anything extra that shouldn't be there.
        consumer.poll(Duration.ofMillis(500)).forEach(received::add);
        return received;
    }

    static List<String> values(List<ConsumerRecord<String, String>> records) {
        return records.stream().map(ConsumerRecord::value).toList();
    }

    private static java.util.Properties propsOf(Map<String, String> map) {
        java.util.Properties props = new java.util.Properties();
        props.putAll(map);
        return props;
    }
}
