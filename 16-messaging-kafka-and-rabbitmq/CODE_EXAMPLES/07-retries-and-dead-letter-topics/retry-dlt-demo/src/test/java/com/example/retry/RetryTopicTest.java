package com.example.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Set;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1)
@ActiveProfiles("retry-topics")
class RetryTopicTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaAdmin admin;

    @Autowired
    PaymentListener listener;

    @Test
    void aFailedRecordWaitsInARetryTopic_soTheRecordsBehindItOvertakeIt() {
        kafka.send("payments", "flaky-A");     // fails once: the gateway timed out
        kafka.send("payments", "payment-B");

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.processed().size() == 2);

        // Sent A then B, processed B then A. Non-blocking retries give up ordering.
        assertThat(listener.processed()).containsExactly("payment-B", "flaky-A");
    }

    @Test
    void afterTheLastAttempt_theRecordGoesToTheDeadLetterTopic() throws Exception {
        kafka.send("payments", "poison-C");

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.deadLetters().contains("poison-C"));

        try (AdminClient client = AdminClient.create(admin.getConfigurationProperties())) {
            Set<String> topics = client.listTopics().names().get();
            assertThat(topics).contains("payments", "payments-retry", "payments-dlt");
        }
    }
}
