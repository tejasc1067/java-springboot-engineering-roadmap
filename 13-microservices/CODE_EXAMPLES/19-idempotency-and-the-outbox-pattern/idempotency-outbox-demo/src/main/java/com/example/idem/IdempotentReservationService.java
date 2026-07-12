package com.example.idem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes "reserve stock" safe to call more than once. The caller sends an idempotency key
 * (a unique id for THIS attempt). The first call with a given key does the work and stores
 * the result; any later call with the SAME key returns the stored result WITHOUT redoing
 * the work. So a retry (topic 10) or a duplicate delivery (topic 07) reserves once, not twice.
 *
 * computeIfAbsent makes the check-and-do atomic per key, so even concurrent duplicates
 * process exactly once. A real service persists processed keys in its database instead of
 * a map, so idempotency survives restarts.
 */
public class IdempotentReservationService {

    public record Reservation(String reservationId) {
    }

    private final Map<String, Reservation> resultsByKey = new ConcurrentHashMap<>();
    private final AtomicInteger workDone = new AtomicInteger();

    public Reservation reserve(String idempotencyKey, String sku, int quantity) {
        return resultsByKey.computeIfAbsent(idempotencyKey, key -> {
            // This block is the actual side-effecting work; it runs at most once per key.
            int n = workDone.incrementAndGet();
            return new Reservation("reservation-" + n);
        });
    }

    /** How many times the real work actually ran (as opposed to how many calls were made). */
    public int workDoneCount() {
        return workDone.get();
    }
}
