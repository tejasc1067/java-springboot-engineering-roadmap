package com.example;

public class DiscountCalculator {

    public long applyPercentOff(long priceCents, int percentOff) {
        if (priceCents < 0) {
            throw new IllegalArgumentException("priceCents must not be negative");
        }
        if (percentOff < 0 || percentOff > 100) {
            throw new IllegalArgumentException(
                    "percentOff must be between 0 and 100, got " + percentOff);
        }
        return priceCents - (priceCents * percentOff / 100);
    }
}
