package com.example.lb;

import java.util.Random;

/** Picks a server for the next request, given how many requests each server is still working on. */
@FunctionalInterface
public interface Strategy {

    int pick(int[] outstanding, long requestNumber);

    static Strategy roundRobin() {
        return (outstanding, n) -> (int) (n % outstanding.length);
    }

    /** Weights like (3, 1): server 0 gets three requests for every one server 1 gets. */
    static Strategy weightedRoundRobin(int... weights) {
        int total = 0;
        for (int w : weights) total += w;
        // Interleaved (0,1,2,3,1,2,3,...), not 1,1,1,...: a block of consecutive requests would queue up.
        int[] schedule = new int[total];
        int slot = 0;
        for (int round = 0; slot < total; round++) {
            for (int server = 0; server < weights.length; server++) {
                if (weights[server] > round) schedule[slot++] = server;
            }
        }
        return (outstanding, n) -> schedule[(int) (n % schedule.length)];
    }

    /** Ties go to the lowest index, so a slow server at index 0 is the worst case for this strategy. */
    static Strategy leastConnections() {
        return (outstanding, n) -> {
            int best = 0;
            for (int i = 1; i < outstanding.length; i++) {
                if (outstanding[i] < outstanding[best]) best = i;
            }
            return best;
        };
    }

    /** Pick two servers at random and send the request to the less busy one. */
    static Strategy powerOfTwoChoices(long seed) {
        Random random = new Random(seed);
        return (outstanding, n) -> {
            int a = random.nextInt(outstanding.length);
            int b = random.nextInt(outstanding.length - 1);
            if (b >= a) b++;   // two different servers
            return outstanding[b] < outstanding[a] ? b : a;
        };
    }
}
