package com.example.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonotonicReadsTest {

    private final SimClock clock = new SimClock();
    private final Leader leader = new Leader(clock);
    private final List<Replica> replicas = List.of(
            new Replica("fast-replica", leader, clock, 100),
            new Replica("slow-replica", leader, clock, 2_000));

    @Test
    void differentReplicasPerRead_theValueGoesBackInTime() {
        RoundRobinReads db = new RoundRobinReads(leader, replicas);
        db.write("admin", "order:42:status", "PAID");
        clock.advance(3_000);
        db.write("admin", "order:42:status", "SHIPPED");
        clock.advance(500);

        List<String> refreshes = new ArrayList<>();
        for (int i = 0; i < 4; i++) refreshes.add(db.read("bob", "order:42:status"));

        // Bob refreshes the page: shipped, then paid again, then shipped...
        assertThat(refreshes).containsExactly("SHIPPED", "PAID", "SHIPPED", "PAID");
    }

    @Test
    void stickyReplicaPerUser_mayBeStale_butNeverGoesBackwards() {
        StickyReads db = new StickyReads(leader, replicas);
        db.write("admin", "order:42:status", "PAID");
        clock.advance(3_000);
        db.write("admin", "order:42:status", "SHIPPED");
        clock.advance(500);

        List<String> refreshes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            refreshes.add(db.read("bob", "order:42:status"));
            clock.advance(500);
        }

        assertThat(db.replicaFor("bob").name()).isEqualTo("slow-replica");
        // Stale for a while, then new, and never back to old.
        assertThat(refreshes).containsExactly("PAID", "PAID", "PAID", "SHIPPED");
    }
}
