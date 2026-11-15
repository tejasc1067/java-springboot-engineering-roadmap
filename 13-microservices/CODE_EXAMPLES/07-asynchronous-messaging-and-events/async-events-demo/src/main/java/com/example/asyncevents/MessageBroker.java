package com.example.asyncevents;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * consumer groups, and lets consumers replay history. Those are module 16.
 */
public class MessageBroker<T> {

    private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
    private final List<T> deadLetters = new CopyOnWriteArrayList<>();
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

    /** Messages that failed every attempt and were parked instead of blocking the queue. */
    public List<T> deadLetters() {
        return deadLetters;
    }

    public boolean consumerAlive() {
        return consumerThread != null && consumerThread.isAlive();
    }

    /** Start a background consumer that applies {@code handler} to each message, as a
        separate consumer service would. Messages already queued are drained first.
        No error handling: an exception from the handler kills the consumer thread. */
    public void startConsumer(Consumer<T> handler) {
        startLoop(handler);
    }

    /** Like {@link #startConsumer}, but a message that fails {@code maxAttempts} times
        is moved to the dead-letter list and the consumer moves on to the next one. */
    public void startConsumerWithDeadLetter(Consumer<T> handler, int maxAttempts) {
        startLoop(message -> {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    handler.accept(message);
                    return;
                } catch (RuntimeException e) {
                    // a real consumer would back off between attempts (topic 10)
                }
            }
            deadLetters.add(message);
        });
    }

    private void startLoop(Consumer<T> handler) {
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
