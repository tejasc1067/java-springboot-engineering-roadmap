package com.example.stampede;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Sends {@code size} concurrent requests for the same missing key while the database holds every load open,
 * then lets the loads finish. Without protection, every request reaches the database before any of them
 * can fill the cache, so the release waits for all of them. With protection, only one load ever starts,
 * so the release happens after a short settle time.
 */
final class Herd {

    static <T> List<T> run(int size, SlowDatabase database, Supplier<T> request) throws Exception {
        CountDownLatch gate = database.holdLoads();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(size)) {
            List<Future<T>> results = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return request.get();
                }));
            }
            start.countDown();
            waitUntilLoadsReach(size, database, Duration.ofSeconds(2));
            gate.countDown();
            List<T> values = new ArrayList<>();
            for (Future<T> result : results) {
                values.add(result.get());
            }
            return values;
        }
    }

    // Returns as soon as `size` loads have started (unprotected), or after the timeout (protected: stuck at 1).
    private static void waitUntilLoadsReach(int size, SlowDatabase database, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (database.loads() < size && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private Herd() { }
}
