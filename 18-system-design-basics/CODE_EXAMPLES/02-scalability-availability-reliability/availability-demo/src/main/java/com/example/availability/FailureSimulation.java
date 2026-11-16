package com.example.availability;

import java.util.Random;

/**
 * Monte Carlo check of the formulas: split time into many independent minutes, roll dice for
 * each component in each minute, and count the minutes in which the whole system worked.
 */
public final class FailureSimulation {

    private final Random random;
    private final int minutes;

    public FailureSimulation(long seed, int minutes) {
        this.random = new Random(seed);
        this.minutes = minutes;
    }

    private boolean up(double availability) {
        return random.nextDouble() < availability;
    }

    /** load balancer -> app -> database: all three must be up. */
    public double series(double balancer, double app, double database) {
        int good = 0;
        for (int m = 0; m < minutes; m++) {
            boolean b = up(balancer), a = up(app), d = up(database);
            if (b && a && d) good++;
        }
        return good / (double) minutes;
    }

    /** Two app instances, independent: either one is enough. */
    public double twoIndependentReplicas(double replica) {
        int good = 0;
        for (int m = 0; m < minutes; m++) {
            boolean first = up(replica), second = up(replica);
            if (first || second) good++;
        }
        return good / (double) minutes;
    }

    /**
     * Two replicas whose failures come from a cause they share (same rack, same power, same bad deploy,
     * same overloaded dependency) plus faults of their own. When the shared cause strikes, both are down.
     */
    public double twoReplicasWithSharedCause(double sharedCauseUp, double ownUp) {
        int good = 0;
        for (int m = 0; m < minutes; m++) {
            boolean shared = up(sharedCauseUp);
            boolean first = shared && up(ownUp), second = shared && up(ownUp);
            if (first || second) good++;
        }
        return good / (double) minutes;
    }
}
