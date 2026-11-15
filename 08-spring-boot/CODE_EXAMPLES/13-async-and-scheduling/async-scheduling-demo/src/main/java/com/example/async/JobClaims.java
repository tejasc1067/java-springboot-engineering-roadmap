package com.example.async;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for a table that every instance shares, with a unique key on the run name:
 * "insert into job_run (name) values ('daily-digest:2026-11-16')" succeeds for exactly one instance.
 * ShedLock is the library that does this for you against your real database.
 */
public final class JobClaims {
    private static final Set<String> CLAIMED = ConcurrentHashMap.newKeySet();

    public static boolean claim(String runName) { return CLAIMED.add(runName); }

    public static void reset() { CLAIMED.clear(); }

    private JobClaims() { }
}
