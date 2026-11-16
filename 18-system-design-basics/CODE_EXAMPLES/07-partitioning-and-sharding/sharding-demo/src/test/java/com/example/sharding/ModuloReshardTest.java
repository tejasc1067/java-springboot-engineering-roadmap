package com.example.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuloReshardTest {

    private final List<String> keys = Keys.users(100_000);

    @Test
    void hashModN_spreadsKeysEvenly() {
        Map<Integer, Integer> load = new HashMap<>();
        keys.forEach(k -> load.merge(ModuloSharding.shardFor(k, 4), 1, Integer::sum));

        assertThat(load).hasSize(4);
        assertThat(Keys.spreadPercent(load.values())).isLessThan(1.0);
    }

    @Test
    void addingOneShard_movesAlmostEveryKey() {
        assertThat(movedFraction(4)).isEqualTo(0.80021);    // 4 -> 5 shards: 80% of the data moves (expected 4/5)
        assertThat(movedFraction(10)).isEqualTo(0.90908);   // 10 -> 11 shards: 91% moves (expected 10/11)
    }

    private double movedFraction(int shards) {
        long moved = keys.stream()
                .filter(k -> ModuloSharding.shardFor(k, shards) != ModuloSharding.shardFor(k, shards + 1))
                .count();
        return moved / (double) keys.size();
    }
}
