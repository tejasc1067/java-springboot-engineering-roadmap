package com.example.sharding;

import java.util.Map;
import java.util.TreeMap;

/**
 * Nodes and keys are hashed onto the same circle of 64-bit numbers. A key belongs to the first node
 * clockwise from it. Each node is placed at {@code virtualNodes} points so the arcs even out.
 */
public final class ConsistentHashRing {

    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodes;

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public void addNode(String node) {
        for (int i = 0; i < virtualNodes; i++) ring.put(Hashing.hash(node + "#" + i), node);
    }

    public void removeNode(String node) {
        for (int i = 0; i < virtualNodes; i++) ring.remove(Hashing.hash(node + "#" + i));
    }

    public String nodeFor(String key) {
        Map.Entry<Long, String> owner = ring.ceilingEntry(Hashing.hash(key));
        return owner != null ? owner.getValue() : ring.firstEntry().getValue();   // wrap around the circle
    }
}
