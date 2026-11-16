package com.example.amqp;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reject failed messages instead of requeueing them; the queue's dead-letter exchange routes them to the DLQ. */
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.default-requeue-rejected=false")
class DeadLetterTest extends RabbitTestBase {

    @Autowired
    ShippingListener shipping;

    @Test
    void failedMessage_isTriedOnce_andLandsInTheDeadLetterQueue() {
        rabbit.convertAndSend(AmqpConfig.ORDERS_EXCHANGE, "order.placed.eu", new OrderPlaced("bad-1", -500));
        rabbit.convertAndSend(AmqpConfig.ORDERS_EXCHANGE, "order.placed.eu", new OrderPlaced("good-1", 1999));

        Message dead = rabbit.receive(AmqpConfig.SHIPPING_DLQ, 5_000);

        assertThat(dead).isNotNull();
        assertThat(rabbit.getMessageConverter().fromMessage(dead)).isEqualTo(new OrderPlaced("bad-1", -500));
        Map<String, ?> death = dead.getMessageProperties().getXDeathHeader().get(0);
        assertThat(death.get("reason")).isEqualTo("rejected");
        assertThat(death.get("queue")).isEqualTo(AmqpConfig.SHIPPING_QUEUE);
        assertThat(shipping.failedAttempts()).isEqualTo(1);
        org.awaitility.Awaitility.await().until(() -> shipping.shipped().size() == 1);
        assertThat(shipping.shipped()).containsExactly(new OrderPlaced("good-1", 1999));   // the next message wasn't blocked
    }
}
