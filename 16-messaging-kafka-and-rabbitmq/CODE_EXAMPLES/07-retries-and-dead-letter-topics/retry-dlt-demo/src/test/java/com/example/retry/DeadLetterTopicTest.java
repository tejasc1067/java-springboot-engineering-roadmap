package com.example.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.util.backoff.FixedBackOff;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
// The dead-letter topic needs at least as many partitions as "orders": a failed record goes to the same partition number.
@EmbeddedKafka(partitions = 1, topics = {"orders", "orders-dlt"})
class DeadLetterTopicTest {

    @TestConfiguration
    static class DeadLetters {

        @Bean
        CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
            DefaultErrorHandler handler = new DefaultErrorHandler(
                    new DeadLetterPublishingRecoverer(kafka),   // after the last retry: publish to "<topic>-dlt"
                    new FixedBackOff(100, 2));                  // 3 attempts, 100 ms apart
            handler.addNotRetryableExceptions(IllegalArgumentException.class);
            return handler;
        }
    }

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    OrderListener listener;

    @Test
    void afterTheRetries_theRecordGoesToTheDeadLetterTopic_withWhyInItsHeaders() {
        kafka.send("orders", "poison-1");
        kafka.send("orders", "order-2");

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.processed().contains("order-2"));
        assertThat(listener.attemptsFor("poison-1")).isEqualTo(3);

        ConsumerRecord<String, String> dead = readDeadLetters(1).stream()
                .filter(record -> record.value().equals("poison-1")).findFirst().orElseThrow();
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo("orders");
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("cannot process poison-1");
    }

    @Test
    void aNotRetryableException_goesStraightToTheDeadLetterTopic() {
        kafka.send("orders", "invalid-3");

        await().atMost(Duration.ofSeconds(20)).until(() -> readDeadLetters(0).stream()
                .anyMatch(record -> record.value().equals("invalid-3")));
        assertThat(listener.attemptsFor("invalid-3")).isEqualTo(1);   // no point retrying a missing customer
    }

    @Test
    void afterTheFix_deadLettersAreReplayedToTheOriginalTopic() {
        kafka.send("orders", "poison-4");
        await().atMost(Duration.ofSeconds(20)).until(() -> readDeadLetters(0).stream()
                .anyMatch(record -> record.value().equals("poison-4")));

        listener.deployFix();
        for (ConsumerRecord<String, String> dead : readDeadLetters(0)) {
            if (dead.value().startsWith("poison")) {
                kafka.send(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC), dead.value());
            }
        }

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.processed().contains("poison-4"));
    }

    /** A fresh consumer group each time, so it always reads the dead-letter topic from the start. */
    private List<ConsumerRecord<String, String>> readDeadLetters(int atLeast) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-reader-" + System.nanoTime(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("orders-dlt"));
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5), Math.max(atLeast, 1)).forEach(records::add);
            return records;
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
