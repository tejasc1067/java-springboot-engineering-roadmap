package com.example.brokers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueueModelTest {

    @Test
    void aConsumedMessage_isGone() {
        QueueBroker broker = new QueueBroker();
        broker.publish("order-1 placed");

        assertThat(broker.receive()).contains("order-1 placed");
        assertThat(broker.receive()).isEmpty();
        assertThat(broker.depth()).isZero();
    }

    @Test
    void competingConsumers_eachMessageGoesToExactlyOne() {
        QueueBroker broker = new QueueBroker();
        for (int i = 1; i <= 6; i++) {
            broker.publish("order-" + i);
        }

        // Two workers of the same email service take turns pulling work off one queue.
        List<String> workerA = new ArrayList<>();
        List<String> workerB = new ArrayList<>();
        Optional<String> next;
        boolean turnA = true;
        while ((next = broker.receive()).isPresent()) {
            (turnA ? workerA : workerB).add(next.get());
            turnA = !turnA;
        }

        assertThat(workerA).hasSize(3);
        assertThat(workerB).hasSize(3);
        assertThat(workerA).doesNotContainAnyElementsOf(workerB);   // no order is emailed twice
    }

    @Test
    void aSecondServiceOnTheSameQueue_stealsMessagesInsteadOfGettingItsOwnCopy() {
        QueueBroker broker = new QueueBroker();
        broker.publish("order-1 placed");
        broker.publish("order-2 placed");

        // The email service and the analytics service both read "the orders queue".
        String forEmail = broker.receive().orElseThrow();
        String forAnalytics = broker.receive().orElseThrow();

        // Each saw only half the orders. One queue can't fan out: every interested
        // service needs its own queue (RabbitMQ does this with exchanges, topic 10).
        assertThat(forEmail).isEqualTo("order-1 placed");
        assertThat(forAnalytics).isEqualTo("order-2 placed");
    }

    @Test
    void aServiceAddedLater_cannotSeeAnythingPublishedBeforeIt() {
        QueueBroker broker = new QueueBroker();
        broker.publish("order-1 placed");
        broker.receive();                        // the email service handled it

        // Next month, a fraud-check service wants every order ever placed. The queue kept none.
        assertThat(broker.receive()).isEmpty();
    }
}
