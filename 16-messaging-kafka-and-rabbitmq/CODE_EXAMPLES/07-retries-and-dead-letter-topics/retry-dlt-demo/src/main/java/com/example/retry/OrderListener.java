package com.example.retry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * "poison..." always fails (a bug, or data the code can't handle).
 * "invalid..." fails with IllegalArgumentException: retrying can never help.
 */
@Component
public class OrderListener {

    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private volatile boolean bugFixed = false;

    @KafkaListener(topics = "orders", groupId = "orders")
    public void on(String order) {
        attempts.merge(order, 1, Integer::sum);
        if (order.startsWith("poison") && !bugFixed) {
            throw new IllegalStateException("cannot process " + order);
        }
        if (order.startsWith("invalid")) {
            throw new IllegalArgumentException("order has no customer: " + order);
        }
        processed.add(order);
    }

    public int attemptsFor(String order) {
        return attempts.getOrDefault(order, 0);
    }

    public List<String> processed() {
        return processed;
    }

    /** Stands in for deploying a fix, before replaying the dead letters. */
    public void deployFix() {
        bugFixed = true;
    }
}
