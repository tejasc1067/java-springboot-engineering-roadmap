package com.example.availability;

import java.time.Duration;

/** Availability arithmetic. An availability is a fraction: 0.999 means up 99.9% of the time. */
public final class Availability {

    static final double HOURS_PER_YEAR = 365 * 24;

    private Availability() { }

    public static Duration downtimePerYear(double availability) {
        return hours((1 - availability) * HOURS_PER_YEAR);
    }

    /** A "month" here is a twelfth of a year (30.4 days), the convention behind the usual tables. */
    public static Duration downtimePerMonth(double availability) {
        return hours((1 - availability) * HOURS_PER_YEAR / 12);
    }

    /** Every component must be up (a request goes through all of them): multiply. */
    public static double series(double... components) {
        double up = 1;
        for (double a : components) up *= a;
        return up;
    }

    /** Any one copy is enough: the system is down only when every copy is down at once. */
    public static double parallel(double... copies) {
        double allDown = 1;
        for (double a : copies) allDown *= 1 - a;
        return 1 - allDown;
    }

    /** Mean time between failures, mean time to repair: fixing faster counts as much as failing less. */
    public static double fromMtbfAndMttr(Duration mtbf, Duration mttr) {
        return mtbf.toSeconds() / (double) (mtbf.toSeconds() + mttr.toSeconds());
    }

    private static Duration hours(double hours) {
        return Duration.ofMillis(Math.round(hours * 3_600_000));
    }
}
