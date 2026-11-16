package com.example.brokers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The log model (Kafka): messages are appended and kept. Reading doesn't remove anything;
 * each consumer group just remembers how far it has read (its offset).
 */
public class LogBroker {

    private final List<String> log = new ArrayList<>();
    private final Map<String, Integer> committedOffsets = new HashMap<>();

    /** Appends a message and returns its offset: its position in the log, starting at 0. */
    public synchronized long publish(String message) {
        log.add(message);
        return log.size() - 1;
    }

    /** Returns up to {@code max} messages after the group's offset, and moves the offset past them. */
    public synchronized List<String> poll(String group, int max) {
        int from = committedOffsets.getOrDefault(group, 0);   // a group it has never seen starts at the beginning
        int to = Math.min(log.size(), from + max);
        committedOffsets.put(group, to);
        return List.copyOf(log.subList(from, to));
    }

    /** Moves a group's position, for example back to 0 to process everything again. */
    public synchronized void seek(String group, int offset) {
        committedOffsets.put(group, offset);
    }

    public synchronized int size() {
        return log.size();
    }
}
