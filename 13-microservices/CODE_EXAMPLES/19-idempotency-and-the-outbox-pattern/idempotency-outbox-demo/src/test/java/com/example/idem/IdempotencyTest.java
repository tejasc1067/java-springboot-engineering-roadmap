package com.example.idem;

import com.example.idem.IdempotentReservationService.Reservation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyTest {

    @Test
    void theSameKeyProcessesOnceAndReturnsTheSameResult() {
        var service = new IdempotentReservationService();

        Reservation first = service.reserve("key-1", "SKU-BOOK", 2);
        Reservation retry = service.reserve("key-1", "SKU-BOOK", 2); // e.g. a retry after a timeout

        assertThat(retry).isEqualTo(first);       // same answer, not a second reservation
        assertThat(service.workDoneCount()).isEqualTo(1); // the work ran ONCE despite two calls
    }

    @Test
    void differentKeysProcessSeparately() {
        var service = new IdempotentReservationService();

        service.reserve("key-1", "SKU-BOOK", 2);
        service.reserve("key-2", "SKU-BOOK", 1);

        assertThat(service.workDoneCount()).isEqualTo(2); // genuinely different requests
    }

    @Test
    void concurrentDuplicatesOfTheSameKeyStillProcessOnce() throws InterruptedException {
        var service = new IdempotentReservationService();
        int threads = 12;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Reservation> results = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                await(start);
                results.add(service.reserve("key-1", "SKU-BOOK", 2)); // all use the SAME key
                done.countDown();
            });
        }
        start.countDown();                 // release all at once
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(service.workDoneCount()).isEqualTo(1); // exactly one reservation, even under a race
        assertThat(results).hasSize(1);                   // everyone got the same result
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
