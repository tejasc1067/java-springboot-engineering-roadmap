package com.example.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Two consumers on one queue share the work (competing consumers). One of them is stuck: it takes messages
 * and never finishes them. Prefetch decides how many messages the broker pushes to a consumer before it acks.
 */
class PrefetchTest extends RabbitTestBase {

    private static final String QUEUE = "image-resize";

    @Autowired
    ConnectionFactory connectionFactory;

    private Connection connection;
    private final AtomicInteger stuckGot = new AtomicInteger();
    private final AtomicInteger healthyGot = new AtomicInteger();

    @BeforeEach
    void declare() {
        admin.declareQueue(new Queue(QUEUE, false, false, false));
        admin.purgeQueue(QUEUE, false);
        connection = connectionFactory.createConnection();
    }

    @AfterEach
    void cleanUp() {
        connection.close();   // the stuck consumer's unacked messages go back to the queue
        admin.deleteQueue(QUEUE);
    }

    private void startConsumers(int prefetch) throws Exception {
        Channel stuck = connection.createChannel(false);
        stuck.basicQos(prefetch);
        stuck.basicConsume(QUEUE, false, new DefaultConsumer(stuck) {
            @Override
            public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties props, byte[] body) {
                stuckGot.incrementAndGet();   // never acks: a hung call, a deadlock, a 10-minute job
            }
        });

        Channel healthy = connection.createChannel(false);
        healthy.basicQos(prefetch);
        healthy.basicConsume(QUEUE, false, new DefaultConsumer(healthy) {
            @Override
            public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties props, byte[] body)
                    throws java.io.IOException {
                healthyGot.incrementAndGet();
                getChannel().basicAck(envelope.getDeliveryTag(), false);
            }
        });
    }

    @Test
    void unlimitedPrefetch_roundRobinHandsHalfTheMessagesToTheStuckConsumer() throws Exception {
        startConsumers(0);   // 0 = no limit

        for (int i = 0; i < 10; i++) publish("", QUEUE, "image " + i);

        await().atMost(Duration.ofSeconds(5)).until(() -> stuckGot.get() + healthyGot.get() == 10);
        assertThat(stuckGot).hasValue(5);     // 5 images wait on a consumer that will never process them
        assertThat(healthyGot).hasValue(5);   // while this one sits idle
    }

    @Test
    void prefetchOne_theStuckConsumerHoldsOneMessage_theHealthyOneDoesTheRest() throws Exception {
        startConsumers(1);

        for (int i = 0; i < 10; i++) publish("", QUEUE, "image " + i);

        await().atMost(Duration.ofSeconds(5)).until(() -> healthyGot.get() == 9);
        assertThat(stuckGot).hasValue(1);
    }
}
