package com.example.kafkaconcepts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import java.util.List;
import java.util.Set;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

// Three brokers in this JVM, so replication has somewhere to put the copies.
@EmbeddedKafka(count = 3, kraft = true)
class TopicsAndPartitionsTest {

    @Test
    void aTopicIsSplitIntoPartitions_eachReplicatedWithOneLeader(EmbeddedKafkaBroker kafka) throws Exception {
        try (AdminClient admin = KafkaClients.admin(kafka.getBrokersAsString())) {
            admin.createTopics(List.of(new NewTopic("orders-replicated", 3, (short) 3))).all().get();

            // Creating a topic returns before every broker has learned about it, so ask until they have.
            TopicDescription orders = await().atMost(Duration.ofSeconds(10)).ignoreExceptions()
                    .until(() -> admin.describeTopics(Set.of("orders-replicated")).allTopicNames().get()
                            .get("orders-replicated"), d -> d.partitions().stream().allMatch(p -> p.isr().size() == 3));

            assertThat(orders.partitions()).hasSize(3);
            for (TopicPartitionInfo partition : orders.partitions()) {
                System.out.printf("partition %d: leader=%d replicas=%s isr=%s%n", partition.partition(),
                        partition.leader().id(), ids(partition.replicas()), ids(partition.isr()));
                assertThat(partition.replicas()).hasSize(3);       // a copy on every broker
                assertThat(partition.isr()).hasSize(3);            // all copies are caught up
                assertThat(partition.replicas()).contains(partition.leader());
            }
            // The leaders are spread over the brokers, so no single broker takes all the traffic.
            assertThat(orders.partitions().stream().map(p -> p.leader().id()).distinct()).hasSizeGreaterThan(1);
        }
    }

    @Test
    void eachPartitionCountsItsOwnOffsets(EmbeddedKafkaBroker kafka) throws Exception {
        try (AdminClient admin = KafkaClients.admin(kafka.getBrokersAsString());
             KafkaProducer<String, String> producer = KafkaClients.producer(kafka.getBrokersAsString())) {
            admin.createTopics(List.of(new NewTopic("orders-offsets", 2, (short) 1))).all().get();

            RecordMetadata a1 = producer.send(new ProducerRecord<>("orders-offsets", 0, null, "a1")).get();
            RecordMetadata b1 = producer.send(new ProducerRecord<>("orders-offsets", 1, null, "b1")).get();
            RecordMetadata a2 = producer.send(new ProducerRecord<>("orders-offsets", 0, null, "a2")).get();
            RecordMetadata a3 = producer.send(new ProducerRecord<>("orders-offsets", 0, null, "a3")).get();
            RecordMetadata b2 = producer.send(new ProducerRecord<>("orders-offsets", 1, null, "b2")).get();

            // There's no offset for "the topic": each partition is its own log, starting at 0.
            assertThat(List.of(a1.offset(), a2.offset(), a3.offset())).containsExactly(0L, 1L, 2L);
            assertThat(List.of(b1.offset(), b2.offset())).containsExactly(0L, 1L);
        }
    }

    @Test
    void orderIsKeptWithinAPartition_notAcrossPartitions(EmbeddedKafkaBroker kafka) throws Exception {
        try (AdminClient admin = KafkaClients.admin(kafka.getBrokersAsString());
             KafkaProducer<String, String> producer = KafkaClients.producer(kafka.getBrokersAsString());
             KafkaConsumer<String, String> consumer = KafkaClients.consumer(kafka.getBrokersAsString(), "ordering")) {
            admin.createTopics(List.of(new NewTopic("orders-ordering", 2, (short) 1))).all().get();

            // Sent in this order: a1, b1, a2, b2, a3
            String[][] sends = {{"0", "a1"}, {"1", "b1"}, {"0", "a2"}, {"1", "b2"}, {"0", "a3"}};
            for (String[] send : sends) {
                producer.send(new ProducerRecord<>("orders-ordering", Integer.parseInt(send[0]), null, send[1])).get();
            }

            consumer.subscribe(List.of("orders-ordering"));
            List<ConsumerRecord<String, String>> received = KafkaClients.pollUntil(consumer, 5);

            // Within each partition: exactly the order they were written.
            assertThat(valuesIn(received, 0)).containsExactly("a1", "a2", "a3");
            assertThat(valuesIn(received, 1)).containsExactly("b1", "b2");
            // Across partitions Kafka promises nothing. Print what this run happened to deliver.
            System.out.println("delivered: " + received.stream().map(ConsumerRecord::value).toList());
        }
    }

    private static List<String> valuesIn(List<ConsumerRecord<String, String>> records, int partition) {
        return records.stream().filter(r -> r.partition() == partition).map(ConsumerRecord::value).toList();
    }

    private static List<Integer> ids(List<org.apache.kafka.common.Node> nodes) {
        return nodes.stream().map(org.apache.kafka.common.Node::id).toList();
    }
}
