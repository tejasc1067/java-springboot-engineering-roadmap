package com.example.delivery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consume-transform-produce: reads a payment, writes a receipt to another topic, and "sends an email".
 * With a transaction-id-prefix configured, Spring runs each delivery in a Kafka transaction:
 * the receipt and the consumer offset commit together, or neither does.
 */
@Component
public class PaymentToReceiptListener {

    private final KafkaTemplate<String, String> kafka;
    private final List<String> emailsSent = new CopyOnWriteArrayList<>();
    private final AtomicBoolean crashOnce = new AtomicBoolean(true);

    public PaymentToReceiptListener(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @KafkaListener(topics = "payments", groupId = "receipts")
    public void on(String payment) {
        kafka.send("receipts", "receipt for " + payment);   // part of the transaction
        emailsSent.add(payment);                              // NOT part of it: nothing can take an email back

        if (crashOnce.getAndSet(false)) {
            throw new IllegalStateException("crash after sending, before the transaction commits");
        }
    }

    public List<String> emailsSent() {
        return emailsSent;
    }
}
