package com.example.replication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the LSN of each user's last write. A read goes to a replica only if that replica has
 * already applied the user's write; otherwise it goes to the leader.
 */
public final class ReadYourWritesReads {

    private final Leader leader;
    private final List<Replica> replicas;
    private final Map<String, Long> lastWriteLsnByUser = new HashMap<>();
    private int leaderReads;

    public ReadYourWritesReads(Leader leader, List<Replica> replicas) {
        this.leader = leader;
        this.replicas = replicas;
    }

    public long write(String user, String key, String value) {
        long lsn = leader.write(key, value);
        lastWriteLsnByUser.put(user, lsn);   // in a web app: a session attribute or a cookie
        return lsn;
    }

    public String read(String user, String key) {
        long mustHave = lastWriteLsnByUser.getOrDefault(user, 0L);
        for (Replica replica : replicas) {
            if (replica.appliedLsn() >= mustHave) return replica.read(key);
        }
        leaderReads++;
        return leader.read(key);
    }

    public int leaderReads() {
        return leaderReads;
    }
}
