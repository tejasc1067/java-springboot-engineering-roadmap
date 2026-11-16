package com.example.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KeysAndPartitionsTest extends KafkaTestBase {

    @Autowired
    OrderPublisher publisher;

    @Test
    void sameKey_alwaysLandsOnTheSamePartition() throws Exception {
        Set<Integer> partitions = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            partitions.add(publisher.publish(new OrderPlaced("o-" + i, "customer-42", 1000)).getRecordMetadata().partition());
        }

        // All of customer-42's orders are in one partition, so they stay in order (topic 05).
        assertThat(partitions).hasSize(1);
    }

    @Test
    void thePartitionIsAHashOfTheKey_moduloThePartitionCount() throws Exception {
        for (String customer : new String[] {"customer-1", "customer-2", "customer-3", "customer-4"}) {
            int actual = publisher.publish(new OrderPlaced("o", customer, 1)).getRecordMetadata().partition();

            // What the default partitioner computes for a record with a key:
            int expected = Utils.toPositive(Utils.murmur2(customer.getBytes(StandardCharsets.UTF_8))) % 3;

            assertThat(actual).as(customer).isEqualTo(expected);
        }
    }
}
