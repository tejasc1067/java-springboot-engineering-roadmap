package com.example.rabbit;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;

/** One RabbitMQ container for the whole run, shared by every test class that extends this. */
@SpringBootTest
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

    /** Publishes and waits until the broker confirms it has routed the message, so queue depths are final. */
    void publish(String exchange, String routingKey, String body) {
        rabbit.invoke(channel -> {
            channel.convertAndSend(exchange, routingKey, body);
            channel.waitForConfirmsOrDie(5_000);
            return null;
        });
    }

    long depth(String queue) {
        return admin.getQueueInfo(queue).getMessageCount();
    }
}
