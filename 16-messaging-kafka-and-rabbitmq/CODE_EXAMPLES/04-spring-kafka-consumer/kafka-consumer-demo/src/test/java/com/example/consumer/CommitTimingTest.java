package com.example.consumer;

import static com.example.consumer.OffsetResetTest.joinAndFixPosition;
import static com.example.consumer.OffsetResetTest.pollUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.MessageListener;

class CommitTimingTest extends KafkaTestBase {

    @Autowired
    ConsumerFactory<String, String> consumers;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ConcurrentKafkaListenerContainerFactory<String, String> factory;

    @Autowired
    KafkaAdmin kafkaAdmin;

    private final List<String> processed = new ArrayList<>();

    @Test
    void commitBeforeProcessing_aCrashLosesTheMessage() throws Exception {
        kafka.send("refunds", "refund-1").get();

        try (Consumer<String, String> first = manualCommitConsumer("refund-service")) {
            first.subscribe(List.of("refunds"));
            pollUntil(first, 1);
            first.commitSync();          // "I've got it"
            // ...and the instance crashes here, before processing.
        }

        try (Consumer<String, String> restarted = manualCommitConsumer("refund-service")) {
            joinAndFixPosition(restarted, "refunds");                     // resumes after the committed offset
            restarted.poll(Duration.ofSeconds(1)).forEach(r -> processed.add(r.value()));
        }

        assertThat(processed).isEmpty();   // the refund is never paid, and no error was logged
    }

    @Test
    void processThenCommit_aCrashInBetweenProcessesTheMessageTwice() throws Exception {
        kafka.send("shipments", "ship-order-1").get();

        try (Consumer<String, String> first = manualCommitConsumer("shipping-service")) {
            first.subscribe(List.of("shipments"));
            pollUntil(first, 1).forEach(r -> processed.add(r.value()));
            // ...the instance crashes here, before commitSync().
        }

        try (Consumer<String, String> restarted = manualCommitConsumer("shipping-service")) {
            restarted.subscribe(List.of("shipments"));
            List<ConsumerRecord<String, String>> again = pollUntil(restarted, 1);
            again.forEach(r -> processed.add(r.value()));
            restarted.commitSync();
        }

        // Nothing lost, but the parcel ships twice unless processing is idempotent (topic 08).
        assertThat(processed).containsExactly("ship-order-1", "ship-order-1");
    }

    @Test
    void springsDefault_commitsAfterTheListenerReturns() throws Exception {
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.BATCH);

        CountDownLatch inListener = new CountDownLatch(1);
        CountDownLatch finishProcessing = new CountDownLatch(1);
        ConcurrentMessageListenerContainer<String, String> container = factory.createContainer("receipts");
        container.getContainerProperties().setGroupId("receipt-printer");
        container.setupMessageListener((MessageListener<String, String>) record -> {
            inListener.countDown();
            try {
                finishProcessing.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        container.start();
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(Duration.ofSeconds(30)).until(() -> container.getAssignedPartitions().size() == 2);
            kafka.send("receipts", "receipt-1").get();

            assertThat(inListener.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(committedTotal(admin, "receipt-printer")).isZero();   // still processing: nothing committed

            finishProcessing.countDown();
            await().atMost(Duration.ofSeconds(10)).until(() -> committedTotal(admin, "receipt-printer") == 1);
        } finally {
            container.stop();
        }
    }

    private Consumer<String, String> manualCommitConsumer(String groupId) {
        Properties props = new Properties();
        props.putAll(Map.of(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
        return consumers.createConsumer(groupId, null, null, props);
    }

    /** The sum of the group's committed offsets: how many records it has marked as done. */
    private static long committedTotal(AdminClient admin, String groupId) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> offsets =
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        return offsets.values().stream().filter(o -> o != null).mapToLong(OffsetAndMetadata::offset).sum();
    }
}
