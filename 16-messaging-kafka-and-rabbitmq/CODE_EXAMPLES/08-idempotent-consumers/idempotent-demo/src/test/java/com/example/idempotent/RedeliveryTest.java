package com.example.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payments-naive", "payments-idempotent"})
class RedeliveryTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    AccountService accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void naiveConsumer_aRedeliveredPaymentIsCreditedTwice() {
        accounts.openAccount("acc-naive");
        kafka.send("payments-naive", new Payment("evt-1", "acc-naive", 100).value());

        await().atMost(Duration.ofSeconds(20)).until(() -> accounts.balanceOf("acc-naive") == 200);
        // One payment of 100, a balance of 200.
    }

    @Test
    void idempotentConsumer_theRedeliveryIsRecognisedAndSkipped() {
        accounts.openAccount("acc-safe");
        kafka.send("payments-idempotent", new Payment("evt-2", "acc-safe", 100).value());
        kafka.send("payments-idempotent", new Payment("evt-3", "acc-safe", 5).value());   // arrives after evt-2's redelivery

        await().atMost(Duration.ofSeconds(20)).until(() -> accounts.balanceOf("acc-safe") == 105);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id = 'evt-2'", Integer.class))
                .isEqualTo(1);
    }
}
