package com.example.cqrs;

import java.util.ArrayList;
import java.util.List;

/** Append-only log of events. The ONLY thing the write side stores - there is no "accounts" table. */
public class EventStore {

    private final List<StoredEvent> log = new ArrayList<>();

    public synchronized List<StoredEvent> streamOf(String accountId) {
        return log.stream().filter(e -> e.accountId().equals(accountId)).toList();
    }

    /**
     * Appends only if the stream is still at the version the caller loaded. If someone else appended
     * in between, the caller decided based on stale state and must reload and decide again.
     * Returns the global position of the last appended event.
     */
    public synchronized long append(String accountId, int expectedVersion, List<AccountEvent> events) {
        int currentVersion = streamOf(accountId).size();
        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException("account " + accountId + " is at version " + currentVersion
                    + ", expected " + expectedVersion);
        }
        return appendWithoutVersionCheck(accountId, events);
    }

    /** The broken version: whoever appends last wins, whatever they saw when they decided. */
    public synchronized long appendWithoutVersionCheck(String accountId, List<AccountEvent> events) {
        int version = streamOf(accountId).size();
        for (AccountEvent event : events) {
            log.add(new StoredEvent(log.size() + 1, accountId, ++version, event));
        }
        return log.size();
    }

    /** Everything after a position, in order. Projections use this to catch up. */
    public synchronized List<StoredEvent> readAllAfter(long position) {
        return log.subList((int) position, log.size()).stream().toList();
    }
}
