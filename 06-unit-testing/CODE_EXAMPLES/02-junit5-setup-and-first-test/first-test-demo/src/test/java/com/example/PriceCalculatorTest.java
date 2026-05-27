package com.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceCalculatorTest {

    // A fresh PriceCalculator is cheap to build, so we just create one for the class.
    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    @DisplayName("three items at 250 cents each cost 750 cents")
    void lineTotalMultipliesPriceByQuantity() {
        long total = calculator.lineTotal(250, 3);

        // assertEquals(expected, actual): expected value first. This is the JUnit ordering.
        assertEquals(750, total);
    }

    @Test
    @DisplayName("zero quantity costs nothing")
    void lineTotalIsZeroWhenQuantityIsZero() {
        assertEquals(0, calculator.lineTotal(999, 0));
    }
}
