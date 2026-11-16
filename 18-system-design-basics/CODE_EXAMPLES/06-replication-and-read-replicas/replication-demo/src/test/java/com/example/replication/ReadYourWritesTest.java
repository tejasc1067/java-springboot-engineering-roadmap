package com.example.replication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReadYourWritesTest {

    private final SimClock clock = new SimClock();
    private final Leader leader = new Leader(clock);
    private final List<Replica> replicas = List.of(
            new Replica("replica-1", leader, clock, 500),
            new Replica("replica-2", leader, clock, 500));

    @Test
    void readsFromAReplica_theUserDoesNotSeeTheirOwnUpdate() {
        RoundRobinReads db = new RoundRobinReads(leader, replicas);
        db.write("ann", "user:ann:name", "Ann");
        clock.advance(1_000);

        db.write("ann", "user:ann:name", "Ann Smith");   // saved, 200 OK
        clock.advance(50);                              // the page reloads 50 ms later

        assertThat(db.read("ann", "user:ann:name")).isEqualTo("Ann");   // the old name
    }

    @Test
    void readsAfterYourOwnWriteWaitForAReplicaThatHasIt_orGoToTheLeader() {
        ReadYourWritesReads db = new ReadYourWritesReads(leader, replicas);
        db.write("ann", "user:ann:name", "Ann");
        clock.advance(1_000);

        db.write("ann", "user:ann:name", "Ann Smith");
        clock.advance(50);

        assertThat(db.read("ann", "user:ann:name")).isEqualTo("Ann Smith");
        assertThat(db.leaderReads()).isEqualTo(1);

        // Other users have no pending write, so they read from a replica and may see the old name.
        assertThat(db.read("bob", "user:ann:name")).isEqualTo("Ann");
        assertThat(db.leaderReads()).isEqualTo(1);

        // Once the replicas catch up, Ann's reads go back to them.
        clock.advance(500);
        assertThat(db.read("ann", "user:ann:name")).isEqualTo("Ann Smith");
        assertThat(db.leaderReads()).isEqualTo(1);
    }
}
