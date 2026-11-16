package com.example.brokers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * The queue model (RabbitMQ, SQS, JMS): a message is handed to one consumer and then it's gone.
 */
public class QueueBroker {

    private final Deque<String> queue = new ArrayDeque<>();

    public synchronized void publish(String message) {
        queue.addLast(message);
    }

    /** Takes the next message off the queue. Nobody else will ever see it. */
    public synchronized Optional<String> receive() {
        return Optional.ofNullable(queue.pollFirst());
    }

    public synchronized int depth() {
        return queue.size();
    }
}
