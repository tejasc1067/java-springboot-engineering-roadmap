package com.example.brokers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Kafka keeps events after they're read, and a group's parallelism is capped by the partition count. */
@EmbeddedKafka(partitions = 2, topics = {"orders-replay", "orders-scaling"})
class KafkaReplayAndScalingTest {

    @Test
    void aNewConsumerGroup_addedLater_readsEveryPastEvent(EmbeddedKafkaBroker broker) {
        send(broker, "orders-replay", 5);

        // Billing has already read them. Months later, analytics is added as a brand-new group.
        assertThat(readAll(broker, "orders-replay", "billing")).hasSize(5);
        assertThat(readAll(broker, "orders-replay", "analytics")).hasSize(5);
    }

    @Test
    void fourConsumersInOneGroup_onTwoPartitions_onlyTwoGetWork(EmbeddedKafkaBroker broker) throws Exception {
        Set<String> threadsThatGotWork = ConcurrentHashMap.newKeySet();
        Map<String, Object> props = KafkaTestUtils.consumerProps("shipping", "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        ContainerProperties containerProps = new ContainerProperties("orders-scaling");
        containerProps.setMessageListener((MessageListener<String, String>) record ->
                threadsThatGotWork.add(Thread.currentThread().getName()));

        var container = new ConcurrentMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()),
                containerProps);
        container.setConcurrency(4);                        // 4 consumers in the "shipping" group
        container.start();
        try {
            // Consumers join one by one, and each join triggers a rebalance. Early on, the first
            // consumer can hold both partitions. Wait until all 4 have joined and the group settles.
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(container.getAssignmentsByClientId()).hasSize(4);
                assertThat(consumersWithAPartition(container)).isEqualTo(2);   // the other 2 sit idle
            });

            send(broker, "orders-scaling", 40);
            await().atMost(Duration.ofSeconds(10)).until(() -> threadsThatGotWork.size() >= 2);
            await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                    .until(() -> threadsThatGotWork.size() == 2);
        } finally {
            container.stop();
        }
    }

    private static long consumersWithAPartition(ConcurrentMessageListenerContainer<?, ?> container) {
        return container.getAssignmentsByClientId().values().stream()
                .filter(partitions -> !partitions.isEmpty())
                .count();
    }

    private static void send(EmbeddedKafkaBroker broker, String topic, int count) {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        try (Producer<String, String> producer =
                     new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()).createProducer()) {
            for (int i = 1; i <= count; i++) {
                producer.send(new ProducerRecord<>(topic, "order-" + i, "OrderPlaced " + i));  // keys spread over both partitions
            }
            producer.flush();
        }
    }

    private static List<String> readAll(EmbeddedKafkaBroker broker, String topic, String group) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(group, "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");   // a new group starts from the oldest event
        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), 5);
            return StreamSupport.stream(records.spliterator(), false).map(ConsumerRecord::value).toList();
        }
    }
}
