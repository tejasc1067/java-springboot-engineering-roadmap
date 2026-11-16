package com.example.amqp;

import org.junit.jupiter.api.AfterEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * One RabbitMQ container for the whole run. Each test gets a fresh application (and so a fresh listener),
 * and the queues are emptied afterwards, so no message or counter leaks from one test into the next.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
abstract class RabbitTestBase {

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static {
        RABBIT.start();
    }

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    RabbitListenerEndpointRegistry listeners;

    @AfterEach
    void stopListenersAndEmptyQueues() {
        listeners.stop();
        admin.purgeQueue(AmqpConfig.SHIPPING_QUEUE, false);
        admin.purgeQueue(AmqpConfig.SHIPPING_DLQ, false);
    }
}
