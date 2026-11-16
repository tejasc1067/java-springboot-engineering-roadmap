package com.example.lb;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 100 clients (say, other services with connection pools) each send 1,000 requests. After their 100th
 * request the pool grows from 2 to 4 instances. Counts are for requests sent after the scale-out.
 */
class LongLivedConnectionsTest {

    static final int CLIENTS = 100, REQUESTS = 1_000, SCALE_OUT_AFTER = 100;

    @Test
    void layer4_connectionsNeverMove_soTheNewInstancesSitIdle() {
        long[] handled = ConnectionBalancing.layer4(CLIENTS, REQUESTS, SCALE_OUT_AFTER, 2, 4, 0);

        System.out.println("L4, keep-alive forever: " + Arrays.toString(handled));
        assertThat(handled).containsExactly(45_000, 45_000, 0, 0);
    }

    @Test
    void layer4_withAConnectionLimit_rebalancesAsClientsReconnect() {
        long[] handled = ConnectionBalancing.layer4(CLIENTS, REQUESTS, SCALE_OUT_AFTER, 2, 4, 100);

        System.out.println("L4, reconnect every 100 requests: " + Arrays.toString(handled));
        assertThat(handled).containsExactly(22_500, 22_500, 22_500, 22_500);
    }

    @Test
    void layer7_balancesEveryRequest_soTheNewInstancesHelpAtOnce() {
        long[] handled = ConnectionBalancing.layer7(CLIENTS, REQUESTS, SCALE_OUT_AFTER, 4);

        assertThat(handled).containsExactly(22_500, 22_500, 22_500, 22_500);
    }
}
