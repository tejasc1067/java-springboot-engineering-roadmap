package com.example.availability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Both cases below use replicas that are each up 99% of the time. What differs is *why* they fail.
 */
class CorrelatedFailureTest {

    static final int MINUTES = 2_000_000;

    @Test
    void independentFailures_aSecondReplicaTurns99Into9999() {
        double one = new FailureSimulation(3, MINUTES).twoReplicasWithSharedCause(1.0, 0.99);

        assertThat(one).isCloseTo(0.9999, within(0.00005));
    }

    @Test
    void sharedCause_aSecondReplicaAddsNothing() {
        // All of each replica's 1% downtime comes from something both share.
        double two = new FailureSimulation(4, MINUTES).twoReplicasWithSharedCause(0.99, 1.0);

        assertThat(two).isCloseTo(0.99, within(0.0002));   // the formula promised 99.99%
    }

    @Test
    void halfShared_mostOfTheGainIsGone() {
        // Each replica: 0.5% from a shared cause, 0.5% from its own faults (still 99% up on its own, about).
        double mixed = new FailureSimulation(5, MINUTES).twoReplicasWithSharedCause(0.995, 0.995);

        assertThat(mixed).isCloseTo(0.995, within(0.0002));   // the shared 0.5% sets the ceiling
    }
}
