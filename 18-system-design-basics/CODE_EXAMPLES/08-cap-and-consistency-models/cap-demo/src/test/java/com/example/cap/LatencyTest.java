package com.example.cap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PACELC's "else": even with no partition, stronger consistency costs latency. */
class LatencyTest {

    private static final long[] REPLICA_LATENCY_MS = {5, 10, 80};   // two nearby replicas, one far away

    @Test
    void waitingForMoreReplicas_meansWaitingForSlowerOnes() {
        assertThat(QuorumStore.quorumLatency(REPLICA_LATENCY_MS, 1)).isEqualTo(5);
        assertThat(QuorumStore.quorumLatency(REPLICA_LATENCY_MS, 2)).isEqualTo(10);
        assertThat(QuorumStore.quorumLatency(REPLICA_LATENCY_MS, 3)).isEqualTo(80);
    }
}
