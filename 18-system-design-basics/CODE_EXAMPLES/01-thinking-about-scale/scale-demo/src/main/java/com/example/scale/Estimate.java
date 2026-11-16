package com.example.scale;

/**
 * Back-of-envelope arithmetic. Every method is one line on purpose: the skill is choosing
 * the inputs and writing them down, not the maths.
 */
public final class Estimate {

    public static final long SECONDS_PER_DAY = 86_400;

    private Estimate() { }

    public static double perSecond(double perDay) {
        return perDay / SECONDS_PER_DAY;
    }

    /** Traffic isn't flat over a day: evenings, Mondays, a marketing email. 2-5x the average is a common guess. */
    public static double peak(double averagePerSecond, double peakFactor) {
        return averagePerSecond * peakFactor;
    }

    public static double bytesPerYear(double writesPerDay, double bytesPerWrite) {
        return writesPerDay * bytesPerWrite * 365;
    }

    public static double bytesPerSecond(double requestsPerSecond, double bytesPerResponse) {
        return requestsPerSecond * bytesPerResponse;
    }

    /**
     * Instances needed so each runs at no more than {@code targetUtilization} of what it can do,
     * plus {@code spare} so one can die (or be redeployed) without overloading the rest.
     */
    public static int instances(double peakPerSecond, double maxPerInstance, double targetUtilization, int spare) {
        return (int) Math.ceil(peakPerSecond / (maxPerInstance * targetUtilization)) + spare;
    }

    /** Little's law: items in the system = arrival rate x time each item spends in it. */
    public static double inFlight(double perSecond, double secondsEach) {
        return perSecond * secondsEach;
    }
}
