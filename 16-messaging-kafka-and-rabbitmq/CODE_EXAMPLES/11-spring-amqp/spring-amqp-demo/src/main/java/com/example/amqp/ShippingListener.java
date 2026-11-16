package com.example.amqp;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ShippingListener {

    private final List<OrderPlaced> shipped = new CopyOnWriteArrayList<>();
    private final AtomicInteger failedAttempts = new AtomicInteger();

    @RabbitListener(queues = AmqpConfig.SHIPPING_QUEUE)
    public void onOrderPlaced(OrderPlaced order) {
        if (order.amountCents() < 0) {
            failedAttempts.incrementAndGet();
            // A bug or bad data: this message will fail the same way however many times it's retried.
            throw new IllegalStateException("negative amount on order " + order.orderId());
        }
        shipped.add(order);
    }

    public List<OrderPlaced> shipped() {
        return shipped;
    }

    public int failedAttempts() {
        return failedAttempts.get();
    }
}
