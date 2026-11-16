package com.example.idempotent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Both listeners "crash" once, after the database transaction committed but before the listener
 * returned, so Kafka's offset is never committed and the payment is delivered again.
 */
@Component
public class PaymentListeners {

    private final AccountService accounts;
    private final Set<String> crashedOnce = ConcurrentHashMap.newKeySet();

    public PaymentListeners(AccountService accounts) {
        this.accounts = accounts;
    }

    @KafkaListener(topics = "payments-naive", groupId = "ledger-naive")
    public void naive(String value) {
        accounts.creditNaive(Payment.parse(value));
        crashOnce(value);
    }

    @KafkaListener(topics = "payments-idempotent", groupId = "ledger-idempotent")
    public void idempotent(String value) {
        accounts.creditOnce(Payment.parse(value));
        crashOnce(value);
    }

    private void crashOnce(String value) {
        if (crashedOnce.add(value)) {
            throw new IllegalStateException("crash after the database commit, before the offset commit");
        }
    }
}
