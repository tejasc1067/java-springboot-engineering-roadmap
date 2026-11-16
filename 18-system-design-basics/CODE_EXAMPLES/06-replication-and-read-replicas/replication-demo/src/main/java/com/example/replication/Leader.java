package com.example.replication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one node that accepts writes. Every write gets the next log sequence number (LSN) and is appended
 * to the replication log, which followers copy from.
 */
public final class Leader {

    public record LogEntry(long lsn, String key, String value, long writtenAtMs) { }

    private final SimClock clock;
    private final Map<String, String> data = new HashMap<>();
    private final List<LogEntry> log = new ArrayList<>();
    private final List<Replica> syncFollowers = new ArrayList<>();
    private boolean crashed;

    public Leader(SimClock clock) {
        this.clock = clock;
    }

    /** Synchronous replication: a write isn't acknowledged until these followers have it. */
    public void replicateSynchronouslyTo(Replica follower) {
        syncFollowers.add(follower);
    }

    /** Returns the write's LSN once it is acknowledged to the caller. */
    public long write(String key, String value) {
        if (crashed) throw new IllegalStateException("leader is down");
        LogEntry entry = new LogEntry(log.size() + 1, key, value, clock.now());
        log.add(entry);
        data.put(key, value);
        if (!syncFollowers.isEmpty()) {
            // Wait for the slowest synchronous follower: the write is as slow as its replication.
            long wait = syncFollowers.stream().mapToLong(Replica::lagMs).max().orElse(0);
            clock.advance(wait);
            syncFollowers.forEach(Replica::catchUp);
        }
        return entry.lsn();
    }

    public String read(String key) {
        if (crashed) throw new IllegalStateException("leader is down");
        return data.get(key);
    }

    public long lastLsn() {
        return log.size();
    }

    List<LogEntry> log() {
        return log;
    }

    public void crash() {
        crashed = true;
    }
}
