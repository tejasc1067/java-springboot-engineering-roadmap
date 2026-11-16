package com.example.msgtest;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Two ways a messaging test goes wrong, each next to its fix. Plain Kafka clients, no Spring context. */
@EmbeddedKafka(partitions = 1)
class BrokenTestPatternsTest {

    // --- 1. A message sent before the consumer has its partition ---

    @Test
    void messageSentBeforeTheConsumerIsAssigned_isSkipped_withTheDefaultLatestReset(EmbeddedKafkaBroker broker) {
        String topic = newTopic(broker);
        List<String> received = new CopyOnWriteArrayList<>();
        KafkaMessageListenerContainer<String, String> container = listener(broker, topic, received);

        send(broker, topic, "order-1:2500");   // the test sends right away...
        container.start();                     // ...and the consumer starts at the END of the partition
        try {
            ContainerTestUtils.waitForAssignment(container, 1);
            await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until(received::isEmpty);
        } finally {
            container.stop();
        }
    }

    @Test
    void waitingForAssignmentFirst_thenSending_isReceived(EmbeddedKafkaBroker broker) {
        String topic = newTopic(broker);
        List<String> received = new CopyOnWriteArrayList<>();
        KafkaMessageListenerContainer<String, String> container = listener(broker, topic, received);

        container.start();
        try {
            ContainerTestUtils.waitForAssignment(container, 1);
            send(broker, topic, "order-1:2500");
            await().atMost(Duration.ofSeconds(10)).until(() -> received.equals(List.of("order-1:2500")));
        } finally {
            container.stop();
        }
    }

    // --- 2. Tests that share a topic see each other's messages ---

    @Test
    void sharedTopic_aLeftoverFromAnotherTest_breaksGetSingleRecord(EmbeddedKafkaBroker broker) {
        String sharedTopic = newTopic(broker);
        send(broker, sharedTopic, "left behind by an earlier test");

        send(broker, sharedTopic, "order-1:2500");   // this test's own message

        try (Consumer<String, String> consumer = consumer(broker)) {
            broker.consumeFromAnEmbeddedTopic(consumer, sharedTopic);
            assertThatThrownBy(() -> KafkaTestUtils.getSingleRecord(consumer, sharedTopic, Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("More than one record");
        }
    }

    @Test
    void topicPerTest_onlyThisTestsMessage(EmbeddedKafkaBroker broker) {
        String myTopic = newTopic(broker);
        send(broker, myTopic, "order-1:2500");

        try (Consumer<String, String> consumer = consumer(broker)) {
            broker.consumeFromAnEmbeddedTopic(consumer, myTopic);
            assertThat(KafkaTestUtils.getSingleRecord(consumer, myTopic, Duration.ofSeconds(5)).value())
                    .isEqualTo("order-1:2500");
        }
    }

    // --- helpers ---

    private static String newTopic(EmbeddedKafkaBroker broker) {
        String topic = "orders-" + UUID.randomUUID();
        broker.addTopics(topic);
        return topic;
    }

    private static KafkaMessageListenerContainer<String, String> listener(
            EmbeddedKafkaBroker broker, String topic, List<String> received) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("shipping-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");   // Kafka's default, written out
        ContainerProperties containerProps = new ContainerProperties(topic);
        containerProps.setMessageListener((MessageListener<String, String>) record -> received.add(record.value()));
        return new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()),
                containerProps);
    }

    private static Consumer<String, String> consumer(EmbeddedKafkaBroker broker) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer();
    }

    private static void send(EmbeddedKafkaBroker broker, String topic, String value) {
        try (Producer<String, String> producer = new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()).createProducer()) {
            producer.send(new ProducerRecord<>(topic, value));
            producer.flush();
        }
    }
}
