package com.example.replication;

/** A clock the tests move by hand, so replica lag is exact and nothing sleeps. */
public final class SimClock {

    private long nowMs;

    public long now() {
        return nowMs;
    }

    public void advance(long ms) {
        nowMs += ms;
    }
}
