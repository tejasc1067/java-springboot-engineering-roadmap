package com.example.brokers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** RabbitMQ deletes a message once it's delivered, and every consumer on a queue shares the work. */
class RabbitReplayAndScalingTest {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static {
        RABBIT.start();
    }

    static final CachingConnectionFactory CONNECTIONS =
            new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    static final RabbitAdmin ADMIN = new RabbitAdmin(CONNECTIONS);
    static final RabbitTemplate TEMPLATE = new RabbitTemplate(CONNECTIONS);

    @AfterAll
    static void close() {
        CONNECTIONS.destroy();
    }

    @Test
    void aQueueBoundAfterPublishing_getsNothing_theEventsAreGone() {
        FanoutExchange orders = new FanoutExchange("orders-replay");
        ADMIN.declareExchange(orders);
        Queue billing = new Queue("billing", false);
        ADMIN.declareQueue(billing);
        ADMIN.declareBinding(BindingBuilder.bind(billing).to(orders));

        for (int i = 1; i <= 5; i++) {
            TEMPLATE.convertAndSend("orders-replay", "", "OrderPlaced " + i);
        }
        for (int i = 1; i <= 5; i++) {
            assertThat(TEMPLATE.receiveAndConvert(billing.getName(), 5_000)).isEqualTo("OrderPlaced " + i);
        }
        assertThat(ADMIN.getQueueInfo(billing.getName()).getMessageCount()).isZero();  // consumed = deleted

        // Analytics is added later, with its own queue on the same exchange.
        Queue analytics = new Queue("analytics", false);
        ADMIN.declareQueue(analytics);
        ADMIN.declareBinding(BindingBuilder.bind(analytics).to(orders));
        assertThat(TEMPLATE.receiveAndConvert(analytics.getName(), 1_000)).isNull();
    }

    @Test
    void fourConsumersOnOneQueue_allFourGetWork() throws Exception {
        Queue queue = new Queue("shipping", false);
        ADMIN.declareQueue(queue);
        Set<String> threadsThatGotWork = ConcurrentHashMap.newKeySet();
        // Each handler waits until 4 messages are being handled at once.
        // That can only happen if 4 different consumers each hold one.
        CountDownLatch fourInProgress = new CountDownLatch(4);

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(CONNECTIONS);
        container.setQueueNames(queue.getName());
        container.setConcurrentConsumers(4);
        container.setPrefetchCount(1);              // one unacked message per consumer at a time
        container.setMessageListener((MessageListener) message -> {
            threadsThatGotWork.add(Thread.currentThread().getName());
            fourInProgress.countDown();
            try {
                fourInProgress.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        container.start();
        try {
            for (int i = 1; i <= 4; i++) {
                TEMPLATE.convertAndSend("", queue.getName(), "OrderPlaced " + i);
            }
            assertThat(fourInProgress.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(threadsThatGotWork).hasSize(4);
        } finally {
            container.stop();
        }
    }
}
