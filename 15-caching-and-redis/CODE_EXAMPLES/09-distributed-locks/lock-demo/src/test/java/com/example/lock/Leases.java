package com.example.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.data.redis.core.StringRedisTemplate;

final class Leases {

    /** Bounded wait for a short lease to run out: stands in for a GC pause or a slow network call. */
    static void awaitExpiry(StringRedisTemplate redis, String key) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (redis.hasKey(key) && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(redis.hasKey(key)).as("lease on " + key + " expired").isFalse();
    }

    private Leases() { }
}
