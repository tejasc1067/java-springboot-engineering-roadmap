package com.example.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.internals.BuiltInPartitioner;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringJUnitConfig
@EmbeddedKafka(partitions = 3, topics = "keyed")
class PartitionKeyTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    /** The same calculation the producer does for a record with a key: murmur2(key) % partitions. */
    static int partitionFor(String key, int partitions) {
        return BuiltInPartitioner.partitionForKey(key.getBytes(StandardCharsets.UTF_8), partitions);
    }

    @Test
    void aKeyAlwaysGoesToTheSamePartition_computedFromItsHash() throws Exception {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < 20; i++) {
                String key = "order-" + i;
                int first = producer.send(new ProducerRecord<>("keyed", key, "a")).get().partition();
                int second = producer.send(new ProducerRecord<>("keyed", key, "b")).get().partition();
                assertThat(second).isEqualTo(first).isEqualTo(partitionFor(key, 3));
            }
        }
    }

    @Test
    void oneHugeCustomerAsTheKey_makesOnePartitionHot() {
        Map<Integer, Integer> perPartition = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String customer = i % 10 < 7 ? "big-retailer" : "customer-" + i;   // 70% of traffic is one customer
            perPartition.merge(partitionFor(customer, 3), 1, Integer::sum);
        }
        int hot = partitionFor("big-retailer", 3);
        assertThat(perPartition.get(hot)).isGreaterThanOrEqualTo(700);
        // One consumer thread handles that partition. Adding consumers can't help: a partition is read by one consumer per group.
    }

    @Test
    void addingPartitions_movesExistingKeysToOtherPartitions() {
        int moved = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "order-" + i;
            if (partitionFor(key, 3) != partitionFor(key, 6)) {
                moved++;
            }
        }
        // Old events of a moved order stay on the old partition; new ones go to the new one.
        // For a while, that order's events are on two partitions and can be read out of order.
        assertThat(moved).isBetween(400, 600);
        System.out.println("keys that changed partition going from 3 to 6 partitions: " + moved + " of 1000");
    }
}
