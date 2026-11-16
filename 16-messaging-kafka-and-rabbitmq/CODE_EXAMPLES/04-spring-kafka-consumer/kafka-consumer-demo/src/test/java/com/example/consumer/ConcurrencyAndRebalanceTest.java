package com.example.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

class ConcurrencyAndRebalanceTest extends KafkaTestBase {

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    ConcurrentKafkaListenerContainerFactory<String, String> factory;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    OrderEmailListener emailListener;

    @Test
    void moreConsumersThanPartitions_theExtraConsumerSitsIdle() {
        ConcurrentMessageListenerContainer<?, ?> email =
                (ConcurrentMessageListenerContainer<?, ?>) registry.getListenerContainer("email");

        await().atMost(Duration.ofSeconds(30)).until(() -> assignedCount(email.getAssignmentsByClientId()) == 2);

        Map<String, Collection<TopicPartition>> byConsumer = email.getAssignmentsByClientId();
        System.out.println("email group assignments: " + byConsumer);
        assertThat(byConsumer).hasSize(3);                                       // concurrency = 3
        assertThat(byConsumer.values()).filteredOn(Collection::isEmpty).hasSize(1);   // but only 2 partitions
    }

    @Test
    void theListenerReceivesWhatIsPublished() {
        kafka.send("orders", "customer-1", "order-1");
        kafka.send("orders", "customer-2", "order-2");

        await().atMost(Duration.ofSeconds(30)).until(() -> emailListener.sent().containsAll(java.util.List.of("order-1", "order-2")));
    }

    @Test
    void aConsumerJoiningOrLeaving_reassignsThePartitions() {
        ConcurrentMessageListenerContainer<String, String> first = container("ledger");
        first.start();
        await().atMost(Duration.ofSeconds(30)).until(() -> first.getAssignedPartitions().size() == 2);

        // A second instance of the ledger service starts: the group rebalances and they split the partitions.
        ConcurrentMessageListenerContainer<String, String> second = container("ledger");
        second.start();
        await().atMost(Duration.ofSeconds(30)).until(() ->
                first.getAssignedPartitions().size() == 1 && second.getAssignedPartitions().size() == 1);
        assertThat(first.getAssignedPartitions()).doesNotContainAnyElementsOf(second.getAssignedPartitions());

        // It stops: another rebalance, and the first instance takes both partitions back.
        second.stop();
        await().atMost(Duration.ofSeconds(30)).until(() -> first.getAssignedPartitions().size() == 2);
        first.stop();
    }

    private ConcurrentMessageListenerContainer<String, String> container(String groupId) {
        ConcurrentMessageListenerContainer<String, String> container = factory.createContainer("payments");
        container.getContainerProperties().setGroupId(groupId);
        container.setupMessageListener((MessageListener<String, String>) record -> { });
        return container;
    }

    private static int assignedCount(Map<String, Collection<TopicPartition>> assignments) {
        return assignments == null ? 0 : assignments.values().stream().mapToInt(Collection::size).sum();
    }
}
