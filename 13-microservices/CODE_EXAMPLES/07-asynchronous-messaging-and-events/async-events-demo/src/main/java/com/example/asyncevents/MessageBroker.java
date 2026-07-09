package com.example.asyncevents;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A stand-in for a real message broker (Kafka/RabbitMQ), backed by an in-memory
 * queue so the demo runs with no Docker. It captures the two properties that make
 * messaging different from a synchronous call:
 *
 *   1. publish() enqueues and returns IMMEDIATELY — it never waits for a consumer.
 *   2. messages sit in the queue until a consumer is running, so a consumer can be
 *      down and catch up later.
 *
 * What a real broker adds beyond this toy: the queue survives process restarts
 * (durability), works across separate machines, fans out to multiple independent
 * consumer groups, and lets consumers replay history. Those are module 17.
 */
public class MessageBroker<T> {

    private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private Thread consumerThread;

    /** Fire-and-forget: hand the message to the broker and return. No consumer needed. */
    public void publish(T message) {
        queue.add(message);
    }

    /** How many messages are waiting to be consumed. */
    public int backlog() {
        return queue.size();
    }

    /** Start a background consumer that applies {@code handler} to each message, as a
        separate consumer service would. Messages already queued are drained first. */
    public void startConsumer(Consumer<T> handler) {
        running = true;
        consumerThread = new Thread(() -> {
            while (running) {
                try {
                    T message = queue.poll(50, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        handler.accept(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "broker-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    public void stopConsumer() throws InterruptedException {
        running = false;
        if (consumerThread != null) {
            consumerThread.join(1000);
        }
    }
}
