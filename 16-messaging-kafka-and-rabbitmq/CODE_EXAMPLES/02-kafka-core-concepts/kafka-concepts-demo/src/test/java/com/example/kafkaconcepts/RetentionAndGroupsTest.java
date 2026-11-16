package com.example.kafkaconcepts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

@EmbeddedKafka(kraft = true)
class RetentionAndGroupsTest {

    private static final TopicPartition PAYMENTS_0 = new TopicPartition("payments", 0);

    @Test
    void consumingDoesNotDelete_aGroupRemembersOnlyAnOffset(EmbeddedKafkaBroker kafka) throws Exception {
        String brokers = kafka.getBrokersAsString();
        try (AdminClient admin = KafkaClients.admin(brokers);
             KafkaProducer<String, String> producer = KafkaClients.producer(brokers)) {
            admin.createTopics(List.of(new NewTopic("payments", 1, (short) 1))).all().get();
            for (String payment : List.of("p1", "p2", "p3")) {
                producer.send(new ProducerRecord<>("payments", payment)).get();
            }

            try (KafkaConsumer<String, String> email = KafkaClients.consumer(brokers, "email")) {
                email.subscribe(List.of("payments"));
                assertThat(values(KafkaClients.pollUntil(email, 3))).containsExactly("p1", "p2", "p3");
                email.commitSync();
            }

            // What Kafka stored for the "email" group: the offset of the NEXT record to read. Nothing else.
            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets("email").partitionsToOffsetAndMetadata().get();
            assertThat(committed.get(PAYMENTS_0).offset()).isEqualTo(3);

            // The records are still in the log. Rewind and they're all there again.
            try (KafkaConsumer<String, String> rereader = KafkaClients.consumer(brokers, "rereader")) {
                rereader.assign(List.of(PAYMENTS_0));
                rereader.seekToBeginning(List.of(PAYMENTS_0));
                assertThat(values(KafkaClients.pollUntil(rereader, 3))).containsExactly("p1", "p2", "p3");
            }

            // A second group, created after everything was consumed, gets its own full copy.
            try (KafkaConsumer<String, String> analytics = KafkaClients.consumer(brokers, "analytics")) {
                analytics.subscribe(List.of("payments"));
                assertThat(values(KafkaClients.pollUntil(analytics, 3))).containsExactly("p1", "p2", "p3");
            }
        }
    }

    private static List<String> values(List<ConsumerRecord<String, String>> records) {
        return records.stream().map(ConsumerRecord::value).toList();
    }
}
