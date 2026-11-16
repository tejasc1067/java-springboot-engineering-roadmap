package com.example.msgtest;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The same service behind RabbitMQ, against a real broker in Docker. */
@SpringBootTest
@ActiveProfiles("rabbit")
class RabbitOrderFlowTest {

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static {
        RABBIT.start();
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitOrderListener listener;
    @Autowired ShippingService shipping;

    @Test
    void validOrder_isShipped() {
        String orderId = "order-" + UUID.randomUUID();

        rabbit.convertAndSend("orders", orderId + ":2500");   // default exchange: routing key = queue name

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(shipping.shipmentFor(orderId)).isPresent());
    }

    @Test
    void invalidOrder_isNotShipped_afterTheListenerProcessedIt() {
        int before = listener.received();

        rabbit.convertAndSend("orders", "order-z:-5");

        await().atMost(Duration.ofSeconds(10)).until(() -> listener.received() > before);
        assertThat(shipping.shipmentFor("order-z")).isEmpty();
    }
}
