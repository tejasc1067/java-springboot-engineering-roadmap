package com.example.scale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 10 workers, 50 ms per request: the pool can finish at most 10 / 0.05 s = 200 requests a second.
 * Push arrivals towards and past that and watch throughput stop growing while waiting explodes.
 */
class SaturationTest {

    static final int WORKERS = 10;
    static final double SERVICE_MS = 50;
    static final long SEED = 42;

    static WorkerPool.Result run(double arrivalsPerSecond) {
        return WorkerPool.run(WORKERS, SERVICE_MS, arrivalsPerSecond, 60, SEED);
    }

    @Test
    void belowCapacity_throughputFollowsDemand_andAlmostNobodyWaits() {
        WorkerPool.Result half = run(100);   // 50% busy

        assertThat(half.throughputPerSecond()).isCloseTo(100, within(5.0));
        assertThat(half.averageWaitMillis()).isLessThan(1);
    }

    @Test
    void nearCapacity_waitingGrowsFastLongBeforeThePoolIsFull() {
        WorkerPool.Result at80 = run(160);
        WorkerPool.Result at95 = run(190);

        assertThat(at80.averageWaitMillis()).isLessThan(10);
        assertThat(at95.averageWaitMillis()).isGreaterThan(at80.averageWaitMillis() * 5);
    }

    @Test
    void pastCapacity_throughputCapsAtWorkersOverServiceTime_andTheQueueNeverDrains() {
        WorkerPool.Result over = run(250);   // 25% more than the pool can do

        assertThat(over.throughputPerSecond()).isCloseTo(200, within(2.0));   // the ceiling: N / latency
        assertThat(over.maxWaitMillis()).isGreaterThan(10_000);               // late arrivals wait >10 s for a 50 ms job
    }

    @Test
    void addingWorkers_raisesTheCeiling() {
        WorkerPool.Result doubled = WorkerPool.run(20, SERVICE_MS, 250, 60, SEED);

        assertThat(doubled.throughputPerSecond()).isCloseTo(250, within(10.0));
        assertThat(doubled.averageWaitMillis()).isLessThan(5);
    }
}
