package com.example.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"})
@EmbeddedKafka(partitions = 1, topics = {"payments", "receipts"})
class NoTransactionTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    PaymentToReceiptListener listener;

    @Test
    void crashAfterSending_withoutATransaction_theReceiptIsSentTwice() throws Exception {
        try (Producer<String, String> producer = Clients.producer(broker, Map.of())) {
            producer.send(new ProducerRecord<>("payments", "payment-1")).get();
        }
        await().atMost(Duration.ofSeconds(20)).until(() -> listener.emailsSent().size() == 2);

        // The first receipt was sent for real before the crash. The redelivery sends another.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(read("read_committed")).containsExactly("receipt for payment-1", "receipt for payment-1"));
    }

    /** Reads until the committed receipt shows up, then once more to prove there's no second one. */
    private List<String> read(String isolationLevel) {
        try (Consumer<String, String> consumer = Clients.consumer(broker, "check-" + isolationLevel,
                Map.of(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel))) {
            consumer.subscribe(List.of("receipts"));
            List<String> values = new ArrayList<>();
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> values.add(record.value()));
                return !values.isEmpty();
            });
            consumer.poll(Duration.ofSeconds(1)).forEach(record -> values.add(record.value()));
            return values;
        }
    }
}
