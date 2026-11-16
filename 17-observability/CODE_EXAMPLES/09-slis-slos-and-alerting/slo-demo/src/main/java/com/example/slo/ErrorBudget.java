package com.example.slo;

import java.time.Duration;

/** The arithmetic behind an SLO: how much failure it allows, and how fast you are spending it. */
public record ErrorBudget(double target, Duration window) {

    public ErrorBudget {
        if (target <= 0 || target >= 1) throw new IllegalArgumentException("target must be between 0 and 1, e.g. 0.999");
    }

    /** The fraction of requests allowed to fail: 1 - target. For 99.9% that's 0.001. */
    public double allowedFailureRatio() {
        return 1 - target;
    }

    /** The budget as time: how long the service could be completely down in the window. */
    public Duration asDowntime() {
        return Duration.ofMillis(Math.round(window.toMillis() * allowedFailureRatio()));
    }

    /** How many times faster than "exactly on budget" you are failing. 1.0 spends the budget exactly by the window's end. */
    public double burnRate(double observedFailureRatio) {
        return observedFailureRatio / allowedFailureRatio();
    }

    /** The fraction of the whole window's budget spent by burning at {@code burnRate} for {@code duration}. */
    public double budgetSpent(double burnRate, Duration duration) {
        return burnRate * duration.toMillis() / window.toMillis();
    }

    /** The burn rate at which {@code fraction} of the budget is spent in {@code duration}: the alert threshold. */
    public double burnRateThatSpends(double fraction, Duration duration) {
        return fraction * window.toMillis() / duration.toMillis();
    }

    /** How long until the whole budget is gone at this burn rate. */
    public Duration timeToExhaustion(double burnRate) {
        return Duration.ofMillis(Math.round(window.toMillis() / burnRate));
    }
}
