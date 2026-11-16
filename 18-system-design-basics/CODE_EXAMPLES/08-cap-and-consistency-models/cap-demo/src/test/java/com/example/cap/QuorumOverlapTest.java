package com.example.cap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuorumOverlapTest {

    private static final List<Integer> WRITE_ORDER = List.of(0, 1, 2);
    private static final List<Integer> READ_ORDER = List.of(2, 1, 0);   // the reader happens to start elsewhere

    @Test
    void w1r1_aReadCanMissTheLatestWrite() {
        QuorumStore store = new QuorumStore(3, 1, 1);
        store.write("price", "100", 1, WRITE_ORDER);
        store.heal();

        store.write("price", "120", 2, WRITE_ORDER);   // acknowledged by replica 0 alone

        assertThat(store.read("price", READ_ORDER)).isEqualTo("100");   // replica 2 hasn't heard yet
    }

    @Test
    void w2r2_theReadAlwaysReachesAReplicaWithTheLatestWrite() {
        QuorumStore store = new QuorumStore(3, 2, 2);
        store.write("price", "100", 1, WRITE_ORDER);
        store.heal();

        store.write("price", "120", 2, WRITE_ORDER);   // replicas 0 and 1

        assertThat(store.read("price", READ_ORDER)).isEqualTo("120");   // asks 2 and 1; 1 has it
    }

    @Test
    void wPlusRGreaterThanN_isExactlyWhenEveryReadSetMeetsEveryWriteSet() {
        int n = 3;
        for (int w = 1; w <= n; w++) {
            for (int r = 1; r <= n; r++) {
                boolean alwaysOverlap = true;
                for (List<Integer> writeSet : subsets(n, w)) {
                    for (List<Integer> readSet : subsets(n, r)) {
                        if (readSet.stream().noneMatch(writeSet::contains)) alwaysOverlap = false;
                    }
                }
                assertThat(alwaysOverlap).as("W=%d R=%d", w, r).isEqualTo(w + r > n);
            }
        }
    }

    private static List<List<Integer>> subsets(int n, int size) {
        List<List<Integer>> result = new ArrayList<>();
        for (int mask = 0; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) != size) continue;
            List<Integer> set = new ArrayList<>();
            for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) set.add(i);
            result.add(set);
        }
        return result;
    }
}
