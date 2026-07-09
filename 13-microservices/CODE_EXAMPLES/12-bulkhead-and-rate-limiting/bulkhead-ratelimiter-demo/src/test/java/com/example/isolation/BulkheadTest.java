package com.example.isolation;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A bulkhead caps how many calls run CONCURRENTLY against one dependency. If a slow
 * dependency saturates its bulkhead, the *excess* calls are rejected fast instead of
 * piling up and consuming every thread in the service — so one slow dependency can't
 * sink the whole ship. (The name is from a ship's watertight compartments.)
 */
class BulkheadTest {

    @Test
    void isolatesConcurrencyRejectingBeyondTheLimitThenReleases() throws InterruptedException {
        Bulkhead bulkhead = Bulkhead.of("inventory", BulkheadConfig.custom()
                .maxConcurrentCalls(2)
                .maxWaitDuration(Duration.ZERO) // don't queue — reject immediately when full
                .build());

        CountDownLatch hold = new CountDownLatch(1);    // keeps the two occupying calls "in progress"
        CountDownLatch bothStarted = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Runnable occupySlot = Bulkhead.decorateRunnable(bulkhead, () -> {
            bothStarted.countDown();  // runs only after a permit is acquired
            await(hold);
        });
        pool.submit(occupySlot);
        pool.submit(occupySlot);

        assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue(); // both slots occupied
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isZero();

        // A third concurrent call finds the bulkhead full and is rejected immediately,
        // rather than blocking a thread waiting on the saturated dependency.
        assertThatThrownBy(() -> Bulkhead.decorateRunnable(bulkhead, () -> { }).run())
                .isInstanceOf(BulkheadFullException.class);

        // Release the two in-progress calls; their slots free up.
        hold.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        // With slots free again, a new call is admitted.
        Bulkhead.decorateRunnable(bulkhead, () -> { }).run(); // no exception
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
