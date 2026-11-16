package com.example.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.util.backoff.FixedBackOff;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "orders")
class InfiniteRetryTest {

    @TestConfiguration
    static class RetryForever {

        // "We never want to lose a message" - so retry forever.
        @Bean
        CommonErrorHandler errorHandler() {
            return new DefaultErrorHandler(new FixedBackOff(10, FixedBackOff.UNLIMITED_ATTEMPTS));
        }
    }

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    OrderListener listener;

    @Test
    void retryingForever_onePoisonRecordBlocksEveryRecordBehindIt() {
        kafka.send("orders", "poison-1");
        kafka.send("orders", "order-2");
        kafka.send("orders", "order-3");

        await().atMost(Duration.ofSeconds(20)).until(() -> listener.attemptsFor("poison-1") >= 10);

        // Records in a partition are handled in order. The partition is stuck until poison-1 succeeds, which is never.
        assertThat(listener.processed()).isEmpty();
        assertThat(listener.attemptsFor("order-2")).isZero();
    }
}
