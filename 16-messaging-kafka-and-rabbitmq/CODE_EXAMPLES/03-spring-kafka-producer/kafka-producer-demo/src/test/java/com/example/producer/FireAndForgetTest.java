package com.example.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.SendResult;

class FireAndForgetTest extends KafkaTestBase {

    // About 20 kB: fine for the producer (its limit is 1 MB), too big for this broker (10 kB, see KafkaTestBase).
    // The producer can't know that, so the error comes back from the broker, after send() has returned.
    private static final OrderPlaced HUGE = new OrderPlaced("x".repeat(20_000), "customer-7", 1);

    @Autowired
    OrderPublisher publisher;

    @Test
    void ignoringTheFuture_theFailureIsSilent() {
        // No exception: as far as this caller knows, the order event was published.
        assertThatCode(() -> publisher.publishAndForget(HUGE)).doesNotThrowAnyException();
    }

    @Test
    void theFutureKnew_itCompletedExceptionally() {
        CompletableFuture<SendResult<String, Object>> future = publisher.publishAsync(HUGE);

        assertThat(future).failsWithin(java.time.Duration.ofSeconds(10))
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(RecordTooLargeException.class);
    }

    @Test
    void waitingForTheResult_theCallerSeesTheError() {
        assertThatThrownBy(() -> publisher.publish(HUGE))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(RecordTooLargeException.class);
    }

    @Test
    void errorsTheClientFindsItself_areThrownStraightFromSend() {
        // 2 MB is over the producer's own 1 MB max.request.size, so it fails before anything is sent.
        OrderPlaced overProducerLimit = new OrderPlaced("x".repeat(2_000_000), "customer-7", 1);

        assertThatThrownBy(() -> publisher.publishAndForget(overProducerLimit))
                .isInstanceOf(KafkaException.class)
                .hasMessageContaining("Send failed")
                .hasRootCauseInstanceOf(RecordTooLargeException.class);
    }

    @Test
    void aSuccessfulSend_tellsYouWhereTheRecordWent() throws Exception {
        SendResult<String, Object> result = publisher.publish(new OrderPlaced("o-1", "customer-7", 1999));

        assertThat(result.getRecordMetadata().topic()).isEqualTo("orders");
        assertThat(result.getRecordMetadata().offset()).isGreaterThanOrEqualTo(0);
    }
}
