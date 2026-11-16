package com.example.replication;

import java.util.HashMap;
import java.util.Map;

/**
 * A follower that copies the leader's log asynchronously. An entry becomes visible here {@code lagMs}
 * after the leader wrote it: that's replica lag.
 */
public final class Replica {

    private final String name;
    private final Leader leader;
    private final SimClock clock;
    private final long lagMs;
    private final Map<String, String> data = new HashMap<>();
    private long appliedLsn;

    public Replica(String name, Leader leader, SimClock clock, long lagMs) {
        this.name = name;
        this.leader = leader;
        this.clock = clock;
        this.lagMs = lagMs;
    }

    void catchUp() {
        for (Leader.LogEntry entry : leader.log()) {
            if (entry.lsn() <= appliedLsn) continue;
            if (entry.writtenAtMs() + lagMs > clock.now()) break;   // not arrived yet
            data.put(entry.key(), entry.value());
            appliedLsn = entry.lsn();
        }
    }

    public String read(String key) {
        catchUp();
        return data.get(key);
    }

    /** The last LSN this replica has applied: "how far behind am I?" in one number. */
    public long appliedLsn() {
        catchUp();
        return appliedLsn;
    }

    /** Failover: the leader is gone and this replica becomes the new leader with whatever it has. */
    public Map<String, String> promote() {
        catchUp();
        return Map.copyOf(data);
    }

    public long lagMs() {
        return lagMs;
    }

    public String name() {
        return name;
    }
}
