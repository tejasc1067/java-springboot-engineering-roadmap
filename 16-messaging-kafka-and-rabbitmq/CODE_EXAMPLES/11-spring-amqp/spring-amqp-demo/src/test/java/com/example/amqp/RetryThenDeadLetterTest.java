package com.example.amqp;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Retry in the listener a few times first (for failures that might be temporary), then reject to the DLQ. */
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=3",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=2"
})
class RetryThenDeadLetterTest extends RabbitTestBase {

    @Autowired
    ShippingListener shipping;

    @Test
    void failedMessage_isRetriedThreeTimes_thenLandsInTheDeadLetterQueue() {
        rabbit.convertAndSend(AmqpConfig.ORDERS_EXCHANGE, "order.placed.eu", new OrderPlaced("bad-1", -500));

        Message dead = rabbit.receive(AmqpConfig.SHIPPING_DLQ, 5_000);

        assertThat(dead).isNotNull();
        assertThat(shipping.failedAttempts()).isEqualTo(3);
        // The retries happened inside the listener: the broker delivered the message once and saw one rejection.
        assertThat(dead.getMessageProperties().getXDeathHeader().get(0).get("count")).isEqualTo(1L);
    }
}
