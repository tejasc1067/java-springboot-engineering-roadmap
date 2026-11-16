package com.example.replication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplicaLagTest {

    private final SimClock clock = new SimClock();
    private final Leader leader = new Leader(clock);
    private final Replica replica = new Replica("replica-1", leader, clock, 500);

    @Test
    void aWriteReachesTheReplicaOnlyAfterTheLag() {
        leader.write("user:ann:name", "Ann");

        assertThat(leader.read("user:ann:name")).isEqualTo("Ann");
        assertThat(replica.read("user:ann:name")).isNull();       // 0 ms: not there yet

        clock.advance(499);
        assertThat(replica.read("user:ann:name")).isNull();       // 499 ms: still not there

        clock.advance(1);
        assertThat(replica.read("user:ann:name")).isEqualTo("Ann");   // 500 ms: caught up
    }

    @Test
    void appliedLsn_saysHowFarBehindTheReplicaIs() {
        leader.write("a", "1");
        clock.advance(300);
        leader.write("b", "2");
        clock.advance(300);
        leader.write("c", "3");

        assertThat(leader.lastLsn()).isEqualTo(3);
        assertThat(replica.appliedLsn()).isEqualTo(1);   // 600 ms: only the first write has arrived
    }
}
