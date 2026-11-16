package com.example.cap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A key-value store copied onto N replicas. A write is acknowledged once W replicas have it; a read asks
 * R replicas and returns the newest value they hold. The other replicas get the write later, in
 * {@link #heal()}. "Which replicas can I reach?" is passed in by the caller, so tests can draw any network
 * partition they like.
 */
public final class QuorumStore {

    public record Versioned(String value, long timestamp) { }

    public static final class UnavailableException extends RuntimeException {
        UnavailableException(String message) {
            super(message);
        }
    }

    private record Pending(int replica, String key, Versioned version) { }

    private final List<Map<String, Versioned>> replicas = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final int writeQuorum;
    private final int readQuorum;

    public QuorumStore(int replicaCount, int writeQuorum, int readQuorum) {
        for (int i = 0; i < replicaCount; i++) replicas.add(new HashMap<>());
        this.writeQuorum = writeQuorum;
        this.readQuorum = readQuorum;
    }

    /**
     * Writes to the first W reachable replicas now and to every other replica later. Refuses the write when
     * fewer than W replicas are reachable: that refusal is the "C" choice during a partition.
     */
    public void write(String key, String value, long timestamp, List<Integer> reachable) {
        if (reachable.size() < writeQuorum) {
            throw new UnavailableException("can reach " + reachable.size() + " replicas, need " + writeQuorum);
        }
        Versioned version = new Versioned(value, timestamp);
        for (int i = 0; i < replicas.size(); i++) {
            if (reachable.indexOf(i) >= 0 && reachable.indexOf(i) < writeQuorum) apply(i, key, version);
            else pending.add(new Pending(i, key, version));
        }
    }

    /** Asks the first R reachable replicas and returns the newest value among them. */
    public String read(String key, List<Integer> reachable) {
        if (reachable.size() < readQuorum) {
            throw new UnavailableException("can reach " + reachable.size() + " replicas, need " + readQuorum);
        }
        return reachable.subList(0, readQuorum).stream()
                .map(i -> replicas.get(i).get(key))
                .filter(v -> v != null)
                .max((a, b) -> Long.compare(a.timestamp(), b.timestamp()))
                .map(Versioned::value)
                .orElse(null);
    }

    /** The network is back: every replica receives the writes it missed. */
    public void heal() {
        pending.forEach(p -> apply(p.replica(), p.key(), p.version()));
        pending.clear();
    }

    public String valueOn(int replica, String key) {
        Versioned v = replicas.get(replica).get(key);
        return v == null ? null : v.value();
    }

    // Last-write-wins: of two versions, keep the one with the larger timestamp. The other is discarded.
    private void apply(int replica, String key, Versioned incoming) {
        replicas.get(replica).merge(key, incoming, (current, next) -> next.timestamp() > current.timestamp() ? next : current);
    }

    /** A write (or read) is as slow as the W-th (or R-th) fastest replica it waits for. */
    public static long quorumLatency(long[] replicaLatenciesMs, int quorum) {
        long[] sorted = replicaLatenciesMs.clone();
        Arrays.sort(sorted);
        return sorted[quorum - 1];
    }
}
