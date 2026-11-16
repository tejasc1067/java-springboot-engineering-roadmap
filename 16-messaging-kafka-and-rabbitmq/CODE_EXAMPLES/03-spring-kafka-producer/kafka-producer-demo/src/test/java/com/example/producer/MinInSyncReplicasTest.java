package com.example.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.NotEnoughReplicasException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

// Two brokers: one partition with a copy on each, and writes with acks=all need both copies in sync.
@EmbeddedKafka(kraft = true, count = 2)
class MinInSyncReplicasTest {

    private static final String TOPIC = "payments";

    @Test
    void acksAll_refusesWrites_whenTooFewCopiesAreInSync_acks1_acceptsThem(EmbeddedKafkaBroker kafka) throws Exception {
        String brokers = kafka.getBrokersAsString();
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", brokers))) {
            // Partition 0 lives on broker 0 (the leader) and broker 1 (the follower).
            admin.createTopics(List.of(new NewTopic(TOPIC, Map.of(0, List.of(0, 1)))
                    .configs(Map.of("min.insync.replicas", "2")))).all().get();
            awaitIsrSize(admin, 2);

            try (KafkaProducer<String, String> acksAll = producer(brokers, "all")) {
                assertThat(acksAll.send(new ProducerRecord<>(TOPIC, "p-1", "100.00")).get().offset()).isZero();
            }

            // Broker 1 goes down. The partition still has a leader, but only one copy.
            ((EmbeddedKafkaKraftBroker) kafka).getCluster().brokers().get(1).shutdown();
            awaitIsrSize(admin, 1);

            try (KafkaProducer<String, String> acksAll = producer(brokers, "all")) {
                // Refused: accepting it would leave the payment one disk failure away from being lost.
                // The error is retriable, so the producer retries until delivery.timeout.ms and then gives up.
                assertThatThrownBy(() -> acksAll.send(new ProducerRecord<>(TOPIC, "p-2", "100.00")).get())
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(TimeoutException.class)
                        .hasMessageContaining("Expiring 1 record(s)");
            }
            try (KafkaProducer<String, String> noRetries = producer(brokers, "all", 0)) {
                // The same send without retries shows the broker's actual answer.
                assertThatThrownBy(() -> noRetries.send(new ProducerRecord<>(TOPIC, "p-2", "100.00")).get())
                        .hasCauseInstanceOf(NotEnoughReplicasException.class);
            }
            try (KafkaProducer<String, String> acks1 = producer(brokers, "1")) {
                // Accepted: the leader alone has it.
                assertThat(acks1.send(new ProducerRecord<>(TOPIC, "p-3", "100.00")).get().offset()).isEqualTo(1);
            }
        }
    }

    private static void awaitIsrSize(AdminClient admin, int size) {
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() -> admin.describeTopics(List.of(TOPIC))
                .allTopicNames().get().get(TOPIC).partitions().get(0).isr().size() == size);
    }

    private static KafkaProducer<String, String> producer(String brokers, String acks) {
        return producer(brokers, acks, Integer.MAX_VALUE);   // the default: retry until the delivery timeout
    }

    private static KafkaProducer<String, String> producer(String brokers, String acks, int retries) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, acks);
        config.put(ProducerConfig.RETRIES_CONFIG, retries);
        // The default delivery timeout is 2 minutes. 3 seconds keeps the test short.
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);
        return new KafkaProducer<>(config);
    }
}
