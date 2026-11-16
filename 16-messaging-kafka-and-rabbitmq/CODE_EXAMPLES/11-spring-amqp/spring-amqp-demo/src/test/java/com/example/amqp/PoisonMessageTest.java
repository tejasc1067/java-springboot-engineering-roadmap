package com.example.amqp;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spring's default: a listener that throws gets its message requeued. A message that can never succeed is
 * redelivered immediately, again and again, and other messages on the queue wait behind it.
 */
class PoisonMessageTest extends RabbitTestBase {

    @Autowired
    ShippingListener shipping;

    @Autowired
    ConnectionFactory connectionFactory;

    @Test
    void failingListener_withDefaultSettings_redeliversTheSameMessageEndlessly() {
        rabbit.convertAndSend(AmqpConfig.ORDERS_EXCHANGE, "order.placed.eu", new OrderPlaced("bad-1", -500));

        await().atMost(Duration.ofSeconds(10)).until(() -> shipping.failedAttempts() >= 100);

        // A hundred attempts in well under a second, and a classic queue would carry on until someone purges it.
        assertThat(rabbit.receive(AmqpConfig.SHIPPING_DLQ)).isNull();
    }

    @Test
    void quorumQueue_givesUpAfterItsDeliveryLimit_andDeadLettersTheMessage() throws Exception {
        String queue = "shipping.orders.quorum";
        admin.declareQueue(QueueBuilder.durable(queue).quorum().deadLetterExchange(AmqpConfig.DEAD_LETTER_EXCHANGE).build());
        rabbit.convertAndSend("", queue, new OrderPlaced("bad-2", -500));

        AtomicInteger deliveries = new AtomicInteger();
        try (Connection connection = connectionFactory.createConnection()) {
            Channel channel = connection.createChannel(false);
            channel.basicConsume(queue, false, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties props, byte[] body)
                        throws java.io.IOException {
                    deliveries.incrementAndGet();
                    getChannel().basicNack(envelope.getDeliveryTag(), false, true);   // fail, and requeue
                }
            });

            assertThat(rabbit.receive(AmqpConfig.SHIPPING_DLQ, 10_000)).isNotNull();
        } finally {
            admin.deleteQueue(queue);
        }
        // RabbitMQ 4 quorum queues have a default delivery limit of 20: the first delivery plus 20 redeliveries.
        assertThat(deliveries).hasValue(21);
    }
}
