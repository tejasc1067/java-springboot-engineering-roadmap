package com.example.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsistentHashingTest {

    private final List<String> keys = Keys.users(100_000);

    @Test
    void addingANode_movesAboutOneFifth_andOnlyToTheNewNode() {
        ConsistentHashRing ring = ringWith(4, 100);
        Map<String, String> before = new HashMap<>();
        keys.forEach(k -> before.put(k, ring.nodeFor(k)));

        ring.addNode("node-4");

        long moved = keys.stream().filter(k -> !ring.nodeFor(k).equals(before.get(k))).count();
        long movedBetweenOldNodes = keys.stream()
                .filter(k -> !ring.nodeFor(k).equals(before.get(k)))
                .filter(k -> !ring.nodeFor(k).equals("node-4"))
                .count();

        assertThat(moved).isEqualTo(19_073);              // 19% of the keys (ideal: 1/5 = 20%)
        assertThat(movedBetweenOldNodes).isZero();        // nothing is shuffled between the old nodes
    }

    @Test
    void removingANode_movesOnlyThatNodesKeys() {
        ConsistentHashRing ring = ringWith(5, 100);
        Map<String, String> before = new HashMap<>();
        keys.forEach(k -> before.put(k, ring.nodeFor(k)));

        ring.removeNode("node-2");

        assertThat(keys).allSatisfy(k -> {
            if (!before.get(k).equals("node-2")) assertThat(ring.nodeFor(k)).isEqualTo(before.get(k));
        });
    }

    @Test
    void oneVirtualNodePerServer_isLopsided_manyVirtualNodesEvenItOut() {
        Map<String, Integer> onePoint = load(ringWith(4, 1));
        Map<String, Integer> hundredPoints = load(ringWith(4, 100));

        // One point per node: the biggest node holds 40,773 keys, the smallest 6,130.
        assertThat(onePoint).containsEntry("node-3", 40_773).containsEntry("node-2", 6_130);
        assertThat(Keys.spreadPercent(onePoint.values())).isBetween(49.0, 50.0);   // standard deviation: 49% of the average
        // 100 points per node: every node is within a few percent of 25,000.
        assertThat(hundredPoints.values()).allSatisfy(n -> assertThat(n).isBetween(23_000, 27_000));
        assertThat(Keys.spreadPercent(hundredPoints.values())).isBetween(4.0, 5.0);   // 4.6%
    }

    private ConsistentHashRing ringWith(int nodes, int virtualNodes) {
        ConsistentHashRing ring = new ConsistentHashRing(virtualNodes);
        for (int i = 0; i < nodes; i++) ring.addNode("node-" + i);
        return ring;
    }

    private Map<String, Integer> load(ConsistentHashRing ring) {
        Map<String, Integer> load = new HashMap<>();
        keys.forEach(k -> load.merge(ring.nodeFor(k), 1, Integer::sum));
        return load;
    }
}
