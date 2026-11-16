package com.example.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = "ledger")
class TransactionsTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void abortedRecords_areStillInTheLog_onlyReadCommittedConsumersSkipThem() throws Exception {
        try (Producer<String, String> producer = Clients.producer(broker,
                Map.of(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "ledger-writer"))) {
            producer.initTransactions();

            producer.beginTransaction();
            producer.send(new ProducerRecord<>("ledger", "debit 50 (aborted)"));
            producer.flush();                 // the record is written to the partition...
            producer.abortTransaction();      // ...and then marked as aborted

            producer.beginTransaction();
            producer.send(new ProducerRecord<>("ledger", "debit 20 (committed)"));
            producer.commitTransaction();
        }

        // Kafka's default for consumers is read_uncommitted.
        assertThat(readAll("read_uncommitted", 2)).containsExactly("debit 50 (aborted)", "debit 20 (committed)");
        assertThat(readAll("read_committed", 1)).containsExactly("debit 20 (committed)");
    }

    private List<String> readAll(String isolationLevel, int expected) {
        try (Consumer<String, String> consumer = Clients.consumer(broker, "audit-" + isolationLevel,
                Map.of(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel))) {
            consumer.subscribe(List.of("ledger"));
            List<String> values = new ArrayList<>();
            for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), expected)) {
                values.add(record.value());
            }
            return values;
        }
    }
}
