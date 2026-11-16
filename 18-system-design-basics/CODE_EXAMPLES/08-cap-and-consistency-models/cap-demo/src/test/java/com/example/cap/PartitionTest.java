package com.example.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Replicas 0 and 1 are on one side of a network split, replica 2 alone on the other. */
class PartitionTest {

    private static final List<Integer> MAJORITY_SIDE = List.of(0, 1);
    private static final List<Integer> MINORITY_SIDE = List.of(2);
    private static final List<Integer> EVERYONE = List.of(0, 1, 2);

    @Test
    void consistentChoice_theMinoritySideRefusesReadsAndWrites() {
        QuorumStore store = new QuorumStore(3, 2, 2);
        store.write("address", "1 Main St", 1_000, EVERYONE);

        store.write("address", "9 Oak Ave", 2_000, MAJORITY_SIDE);   // fine: 2 of 3 reachable

        assertThatThrownBy(() -> store.write("address", "5 Elm Rd", 3_000, MINORITY_SIDE))
                .isInstanceOf(QuorumStore.UnavailableException.class);
        assertThatThrownBy(() -> store.read("address", MINORITY_SIDE))
                .isInstanceOf(QuorumStore.UnavailableException.class);
        // Users near replica 2 get errors, but nobody ever reads a wrong address.
        assertThat(store.read("address", MAJORITY_SIDE)).isEqualTo("9 Oak Ave");
    }

    @Test
    void availableChoice_bothSidesAcceptWrites_andLastWriteWinsLosesOneOfThem() {
        QuorumStore store = new QuorumStore(3, 1, 1);
        store.write("address", "1 Main St", 0, EVERYONE);
        store.heal();

        // Real time 1,000 ms: Ann, on the majority side, saves a new address.
        store.write("address", "9 Oak Ave", 1_000, MAJORITY_SIDE);
        // Real time 2,000 ms: Ann corrects it from her phone, which reaches only replica 2. That server's
        // clock is 1,500 ms slow, so it stamps the write 500.
        store.write("address", "5 Elm Rd", 2_000 - 1_500, MINORITY_SIDE);

        // During the split each side answers, with different data.
        assertThat(store.read("address", MAJORITY_SIDE)).isEqualTo("9 Oak Ave");
        assertThat(store.read("address", MINORITY_SIDE)).isEqualTo("5 Elm Rd");

        store.heal();

        // Both writes were acknowledged. The newer one, by real time, is gone on every replica.
        assertThat(List.of(store.valueOn(0, "address"), store.valueOn(1, "address"), store.valueOn(2, "address")))
                .containsOnly("9 Oak Ave");
    }
}
