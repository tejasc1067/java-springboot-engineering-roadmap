package com.example.brokers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogModelTest {

    @Test
    void readingDoesNotRemove_messagesStayInTheLog() {
        LogBroker broker = new LogBroker();
        broker.publish("order-1 placed");

        assertThat(broker.poll("email", 10)).containsExactly("order-1 placed");
        assertThat(broker.size()).isEqualTo(1);              // still there
        assertThat(broker.poll("email", 10)).isEmpty();      // but "email" has moved past it
    }

    @Test
    void offsets_areJustPositionsInTheLog() {
        LogBroker broker = new LogBroker();

        assertThat(broker.publish("order-1 placed")).isZero();
        assertThat(broker.publish("order-2 placed")).isEqualTo(1);
        assertThat(broker.publish("order-3 placed")).isEqualTo(2);
    }

    @Test
    void everyConsumerGroup_getsEveryMessage_atItsOwnPace() {
        LogBroker broker = new LogBroker();
        broker.publish("order-1 placed");
        broker.publish("order-2 placed");
        broker.publish("order-3 placed");

        assertThat(broker.poll("email", 10))
                .containsExactly("order-1 placed", "order-2 placed", "order-3 placed");
        assertThat(broker.poll("analytics", 1)).containsExactly("order-1 placed");   // slower, that's fine
        assertThat(broker.poll("analytics", 10)).containsExactly("order-2 placed", "order-3 placed");
    }

    @Test
    void aGroupAddedLater_replaysFromOffsetZero() {
        LogBroker broker = new LogBroker();
        broker.publish("order-1 placed");
        broker.publish("order-2 placed");
        broker.poll("email", 10);

        // Next month: fraud-check is deployed and reads the whole history.
        List<String> history = broker.poll("fraud-check", 10);

        assertThat(history).containsExactly("order-1 placed", "order-2 placed");
    }

    @Test
    void afterABugFix_aGroupCanRewindAndProcessEverythingAgain() {
        LogBroker broker = new LogBroker();
        broker.publish("order-1 placed");
        broker.publish("order-2 placed");
        broker.poll("analytics", 10);            // processed with a bug

        broker.seek("analytics", 0);

        assertThat(broker.poll("analytics", 10)).containsExactly("order-1 placed", "order-2 placed");
    }
}
