package com.example.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FailoverTest {

    private final SimClock clock = new SimClock();
    private final Leader leader = new Leader(clock);

    @Test
    void asyncReplication_failoverLosesWritesTheLeaderAlreadyAcknowledged() {
        Replica follower = new Replica("follower", leader, clock, 1_000);

        leader.write("order:1", "PLACED");
        clock.advance(1_500);
        leader.write("order:2", "PLACED");   // each returned "OK" to its client
        clock.advance(100);
        leader.write("order:3", "PLACED");
        clock.advance(100);

        leader.crash();
        Map<String, String> newLeader = follower.promote();

        assertThat(newLeader).containsOnlyKeys("order:1");   // orders 2 and 3 are gone
    }

    @Test
    void syncReplication_noAcknowledgedWriteIsLost_butEveryWritePaysTheReplicationTime() {
        Replica follower = new Replica("follower", leader, clock, 1_000);
        leader.replicateSynchronouslyTo(follower);

        long before = clock.now();
        leader.write("order:1", "PLACED");
        leader.write("order:2", "PLACED");
        leader.write("order:3", "PLACED");
        long elapsed = clock.now() - before;

        leader.crash();
        Map<String, String> newLeader = follower.promote();

        assertThat(newLeader).containsOnlyKeys("order:1", "order:2", "order:3");
        assertThat(elapsed).isEqualTo(3_000);   // each write waited 1,000 ms for the follower
    }
}
