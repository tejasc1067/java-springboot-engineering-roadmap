package com.example.stateless;

import java.util.Map;

/** Locks an account after {@code MAX_FAILURES} wrong passwords. The counts live wherever the map lives. */
public class LoginGuard {

    public static final int MAX_FAILURES = 3;

    private final Map<String, Integer> failures;

    public LoginGuard(Map<String, Integer> failures) {
        this.failures = failures;
    }

    /** Returns false when the account is locked; otherwise records a failed attempt and returns true. */
    public boolean tryWrongPassword(String user) {
        if (failures.getOrDefault(user, 0) >= MAX_FAILURES) return false;
        failures.merge(user, 1, Integer::sum);
        return true;
    }
}
