package com.example.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** No error handler configured: Spring Kafka's DefaultErrorHandler with its default FixedBackOff(0, 9). */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "orders")
class DefaultErrorHandlerTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    OrderListener listener;

    @Test
    void byDefault_aFailingRecordIsTriedTenTimes_thenLoggedAndSkipped() {
        kafka.send("orders", "poison-1");
        kafka.send("orders", "order-2");

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.processed().contains("order-2"));

        assertThat(listener.attemptsFor("poison-1")).isEqualTo(10);   // 1 attempt + 9 retries, no delay between them
        assertThat(listener.processed()).containsExactly("order-2");   // poison-1 is gone: only an ERROR log line remains
    }
}
