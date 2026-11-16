package com.example.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // the dead-letter record keeps the original, undecodable bytes
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer"})
@EmbeddedKafka(partitions = 1, topics = {"shipments", "shipments-dlt", "orders"})
@ActiveProfiles("shipments")
class DeserializationErrorTest {

    @TestConfiguration
    static class DeadLetters {

        @Bean
        CommonErrorHandler errorHandler(KafkaTemplate<Object, Object> kafka) {
            return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka));
        }
    }

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    KafkaTemplate<Object, Object> kafka;

    @Autowired
    ShipmentListener listener;

    @Test
    void badJson_withErrorHandlingDeserializer_goesToTheDeadLetterTopicOnTheFirstTry() {
        kafka.send("shipments", "{not json".getBytes(StandardCharsets.UTF_8));
        kafka.send("shipments", "{\"orderId\":\"order-1\",\"carrier\":\"DHL\"}".getBytes(StandardCharsets.UTF_8));

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.received().size() == 1);
        assertThat(listener.received()).containsExactly(new Shipment("order-1", "DHL"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-reader", "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(props, new ByteArrayDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(java.util.List.of("shipments-dlt"));
            ConsumerRecord<byte[], byte[]> dead = KafkaTestUtils.getSingleRecord(consumer, "shipments-dlt", Duration.ofSeconds(10));
            assertThat(new String(dead.value(), StandardCharsets.UTF_8)).isEqualTo("{not json");
        }
    }
}
