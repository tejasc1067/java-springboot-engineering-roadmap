package com.example.ttl;

import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Duration;

/**
 * A clock the test moves by hand. Caffeine reads time through a Ticker, so "wait 5 minutes"
 * becomes advance(Duration.ofMinutes(5)) and the tests never sleep.
 */
public class FakeTicker implements Ticker {

    private long nanos;

    @Override
    public long read() {
        return nanos;
    }

    public void advance(Duration duration) {
        nanos += duration.toNanos();
    }
}
