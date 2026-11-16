package com.example.lb;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * A discrete-event simulation of a load balancer in front of N servers.
 * Requests arrive at a fixed interval. Each server handles one request at a time, in arrival order,
 * and takes a fixed time per request. Time is simulated in microseconds; nothing sleeps.
 */
public final class LoadBalancerSimulation {

    public record Result(long[] requestsPerServer, long p50Micros, long p99Micros, long maxMicros) {
        public long p99Millis() { return p99Micros / 1000; }
        public long p50Millis() { return p50Micros / 1000; }
        public long maxMillis() { return maxMicros / 1000; }
    }

    public static Result run(Strategy strategy, long[] serviceMicros, long arrivalEveryMicros, int requests) {
        int servers = serviceMicros.length;
        long[] freeAt = new long[servers];
        @SuppressWarnings("unchecked")
        ArrayDeque<Long>[] inFlight = new ArrayDeque[servers];   // finish times, oldest first
        for (int i = 0; i < servers; i++) inFlight[i] = new ArrayDeque<>();
        long[] perServer = new long[servers];
        long[] latencies = new long[requests];
        int[] outstanding = new int[servers];

        for (int n = 0; n < requests; n++) {
            long now = n * arrivalEveryMicros;
            for (int i = 0; i < servers; i++) {
                while (!inFlight[i].isEmpty() && inFlight[i].peekFirst() <= now) inFlight[i].pollFirst();
                outstanding[i] = inFlight[i].size();
            }
            int s = strategy.pick(outstanding, n);
            long start = Math.max(now, freeAt[s]);
            long finish = start + serviceMicros[s];
            freeAt[s] = finish;
            inFlight[s].addLast(finish);
            perServer[s]++;
            latencies[n] = finish - now;
        }

        Arrays.sort(latencies);
        return new Result(perServer,
                latencies[(int) (requests * 0.50)],
                latencies[(int) (requests * 0.99)],
                latencies[requests - 1]);
    }
}
