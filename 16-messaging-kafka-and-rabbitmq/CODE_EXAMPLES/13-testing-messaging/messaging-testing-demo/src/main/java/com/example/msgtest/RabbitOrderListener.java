package com.example.msgtest;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.atomic.AtomicInteger;

/** The same service behind a RabbitMQ queue. */
@Configuration
@Profile("rabbit")
public class RabbitOrderListener {

    private final ShippingService shipping;
    private final AtomicInteger received = new AtomicInteger();

    public RabbitOrderListener(ShippingService shipping) {
        this.shipping = shipping;
    }

    @Bean
    Queue ordersQueue() {
        return new Queue("orders");
    }

    @RabbitListener(queues = "orders")
    public void onOrder(String orderEvent) {
        received.incrementAndGet();
        shipping.handle(orderEvent);
    }

    public int received() {
        return received.get();
    }
}
