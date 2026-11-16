package com.example.replication;

import java.util.List;

/** Broken for users who just wrote: writes go to the leader, every read to the next replica in turn. */
public final class RoundRobinReads {

    private final Leader leader;
    private final List<Replica> replicas;
    private int next;

    public RoundRobinReads(Leader leader, List<Replica> replicas) {
        this.leader = leader;
        this.replicas = replicas;
    }

    public long write(String user, String key, String value) {
        return leader.write(key, value);
    }

    public String read(String user, String key) {
        Replica replica = replicas.get(next++ % replicas.size());
        return replica.read(key);
    }
}
