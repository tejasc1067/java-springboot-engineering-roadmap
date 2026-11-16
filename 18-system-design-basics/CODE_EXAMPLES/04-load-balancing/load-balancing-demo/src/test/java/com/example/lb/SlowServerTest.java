package com.example.lb;

import com.example.lb.LoadBalancerSimulation.Result;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Four servers. Server 0 is sick: 100 ms per request instead of 10 ms (a bad disk, a noisy neighbour,
 * a long GC). A request arrives every 4 ms, 250 per second, for 40 seconds. Total capacity is
 * 3 x 100 + 10 = 310 requests per second, so the fleet as a whole can keep up.
 */
class SlowServerTest {

    static final long[] ONE_SLOW = {100_000, 10_000, 10_000, 10_000};
    static final long[] ALL_HEALTHY = {10_000, 10_000, 10_000, 10_000};
    static final long ARRIVAL_EVERY = 4_000;
    static final int REQUESTS = 10_000;

    static Result run(Strategy strategy, long[] servers) {
        Result result = LoadBalancerSimulation.run(strategy, servers, ARRIVAL_EVERY, REQUESTS);
        System.out.printf("p50=%d ms p99=%d ms max=%d ms per server=%s%n",
                result.p50Millis(), result.p99Millis(), result.maxMillis(),
                Arrays.toString(result.requestsPerServer()));
        return result;
    }

    @Test
    void allHealthy_roundRobinIsPerfect() {
        Result result = run(Strategy.roundRobin(), ALL_HEALTHY);

        assertThat(result.requestsPerServer()).containsExactly(2_500, 2_500, 2_500, 2_500);
        assertThat(result.p99Millis()).isEqualTo(10);
    }

    @Test
    void roundRobin_keepsFeedingTheSlowServer_andItsQueueGrowsForever() {
        Result result = run(Strategy.roundRobin(), ONE_SLOW);

        // A quarter of all traffic goes to a server that can handle a tenth of what it receives.
        assertThat(result.requestsPerServer()).containsExactly(2_500, 2_500, 2_500, 2_500);
        assertThat(result.p50Millis()).isEqualTo(10);              // the median looks fine...
        assertThat(result.p99Millis()).isGreaterThan(200_000);     // ...p99 is minutes
    }

    @Test
    void leastConnections_sendsTheSlowServerOnlyWhatItCanFinish() {
        Result result = run(Strategy.leastConnections(), ONE_SLOW);

        assertThat(result.requestsPerServer()[0]).isLessThan(500);
        assertThat(result.p99Millis()).isLessThanOrEqualTo(100);   // one slow request, never a queue
    }

    @Test
    void powerOfTwoChoices_getsMostOfTheProtection_withoutLookingAtEveryServer() {
        Result result = run(Strategy.powerOfTwoChoices(42), ONE_SLOW);

        // Two random servers, both idle, one of them the slow one: a coin flip can still pick it,
        // so a few requests wait behind another slow one. Hundreds of ms, not minutes.
        assertThat(result.requestsPerServer()[0]).isLessThan(500);
        assertThat(result.p99Millis()).isBetween(101L, 500L);
    }

    @Test
    void weightedRoundRobin_splitsByWeight_butStillIgnoresHowBusyAServerIs() {
        Result result = run(Strategy.weightedRoundRobin(1, 10, 10, 10), ONE_SLOW);

        // 1/31 of 250 req/s is about 8 req/s, under the slow server's 10 req/s: weights you got right.
        assertThat(result.requestsPerServer()[0]).isEqualTo(323);
        assertThat(result.p99Millis()).isLessThanOrEqualTo(100);

        // Weights are static: if the server gets slower than you assumed, its queue grows again.
        Result slower = run(Strategy.weightedRoundRobin(1, 10, 10, 10),
                new long[] {200_000, 10_000, 10_000, 10_000});
        assertThat(slower.p99Millis()).isGreaterThan(10_000);
    }
}
