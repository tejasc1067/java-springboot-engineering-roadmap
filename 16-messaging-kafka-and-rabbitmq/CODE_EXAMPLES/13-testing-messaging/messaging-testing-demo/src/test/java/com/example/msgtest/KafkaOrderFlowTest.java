package com.example.msgtest;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The whole flow through a real (embedded) Kafka broker: orders topic -> listener -> service -> shipments topic. */
@SpringBootTest
@ActiveProfiles("kafka")
@EmbeddedKafka(partitions = 1, topics = {"orders", "shipments"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class KafkaOrderFlowTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired KafkaOrderListener listener;
    @Autowired ShippingService shipping;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired EmbeddedKafkaBroker broker;

    @BeforeEach
    void waitUntilTheListenerOwnsItsPartition() {
        // The listener uses Kafka's default auto.offset.reset=latest: a message sent before it owns the
        // partition is skipped. On a fast laptop the listener usually wins that race; on a busy CI
        // machine it may not. BrokenTestPatternsTest forces the losing order. Don't race: wait.
        ContainerTestUtils.waitForAssignment(registry.getListenerContainer("shipping"), 1);
    }

    @Test
    void validOrder_isShipped_andAShipmentEventIsPublished() {
        String orderId = "order-" + UUID.randomUUID();   // unique per test: other tests share these topics

        kafka.send("orders", orderId + ":2500");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(shipping.shipmentFor(orderId)).isPresent());
        assertThat(shipmentEventsFor(orderId)).containsExactly("ship-" + orderId);
    }

    @Test
    void typoInTheTopic_theTestStillPasses_becauseItOnlyCheckedThatNothingHappened() {
        int before = listener.received();

        kafka.send("order", "order-x:-5");               // "order", not "orders": nobody listens there

        assertThat(shipping.shipmentFor("order-x")).isEmpty();   // green, and proves nothing
        assertThat(listener.received()).isEqualTo(before);      // the listener never even saw it
    }

    @Test
    void invalidOrder_isNotShipped_provedByWaitingUntilTheListenerProcessedIt() {
        int before = listener.received();

        kafka.send("orders", "order-y:-5");

        // First: something DID happen. The listener processed our message.
        await().atMost(Duration.ofSeconds(10)).until(() -> listener.received() > before);
        // Then: the thing that must not happen didn't, and still hasn't a moment later.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .until(() -> shipping.shipmentFor("order-y").isEmpty());
    }

    private List<String> shipmentEventsFor(String orderId) {
        // consumerProps defaults to Integer keys; this topic has String keys, so pass the deserializers.
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "shipments");
            // The topic also holds other tests' shipments: pick ours by key.
            return StreamSupport.stream(KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5)).spliterator(), false)
                    .filter(record -> record.key().equals(orderId))
                    .map(ConsumerRecord::value)
                    .toList();
        }
    }
}
