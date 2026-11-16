package com.example.retry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Non-blocking retries: a failed payment is moved to "payments-retry" and tried again after the delay,
 * while the main topic carries on with the next payment.
 */
@Component
@Profile("retry-topics")
public class PaymentListener {

    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final List<String> deadLetters = new CopyOnWriteArrayList<>();
    private final AtomicBoolean gatewayDownOnce = new AtomicBoolean(true);

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000))
    @KafkaListener(topics = "payments", groupId = "payments")
    public void on(String payment) {
        if (payment.startsWith("flaky") && gatewayDownOnce.getAndSet(false)) {
            throw new IllegalStateException("payment gateway timed out");
        }
        if (payment.startsWith("poison")) {
            throw new IllegalStateException("cannot process " + payment);
        }
        processed.add(payment);
    }

    @DltHandler
    public void onDeadLetter(String payment) {
        deadLetters.add(payment);
    }

    public List<String> processed() {
        return processed;
    }

    public List<String> deadLetters() {
        return deadLetters;
    }
}
