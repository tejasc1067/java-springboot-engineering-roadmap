package com.example.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer"})
@EmbeddedKafka(partitions = 1, topics = {"shipments", "orders"})
@ActiveProfiles("shipments-broken")
class DeserializationErrorBrokenTest {

    static final AtomicInteger POLL_FAILURES = new AtomicInteger();

    @TestConfiguration
    static class CountingHandler {

        /** The default handler, counting the failures that happen inside poll(), before any listener runs. */
        @Bean
        CommonErrorHandler errorHandler() {
            return new DefaultErrorHandler() {
                @Override
                public void handleOtherException(Exception thrown, Consumer<?, ?> consumer,
                        MessageListenerContainer container, boolean batchListener) {
                    POLL_FAILURES.incrementAndGet();
                    super.handleOtherException(thrown, consumer, container, batchListener);
                }
            };
        }
    }

    @Autowired
    KafkaTemplate<Object, Object> kafka;

    @Autowired
    ShipmentListenerBroken listener;

    @Test
    void badJson_withoutErrorHandlingDeserializer_failsEveryPoll_andBlocksThePartition() {
        kafka.send("shipments", "{not json".getBytes(StandardCharsets.UTF_8));
        kafka.send("shipments", "{\"orderId\":\"order-1\",\"carrier\":\"DHL\"}".getBytes(StandardCharsets.UTF_8));

        await().atMost(Duration.ofSeconds(30)).until(() -> POLL_FAILURES.get() >= 5);

        // No record ever reached the listener, so there is nothing to retry, skip, or dead-letter.
        // The consumer polls, fails on the same offset, and polls again, logging an error each time.
        assertThat(listener.received()).isEmpty();
    }
}
