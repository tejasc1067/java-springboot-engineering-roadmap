package com.example.replication;

import java.util.List;

/** Each user always reads from the same replica, so their view never moves backwards in time. */
public final class StickyReads {

    private final Leader leader;
    private final List<Replica> replicas;

    public StickyReads(Leader leader, List<Replica> replicas) {
        this.leader = leader;
        this.replicas = replicas;
    }

    public long write(String user, String key, String value) {
        return leader.write(key, value);
    }

    public String read(String user, String key) {
        return replicas.get(Math.floorMod(user.hashCode(), replicas.size())).read(key);
    }

    public Replica replicaFor(String user) {
        return replicas.get(Math.floorMod(user.hashCode(), replicas.size()));
    }
}
