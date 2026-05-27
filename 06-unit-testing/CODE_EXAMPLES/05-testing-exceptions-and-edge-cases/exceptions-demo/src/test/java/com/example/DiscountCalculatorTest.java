package com.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscountCalculatorTest {

    private final DiscountCalculator calc = new DiscountCalculator();

    // --- the happy path ---

    @Test
    void appliesAStraightforwardDiscount() {
        assertThat(calc.applyPercentOff(1000, 20)).isEqualTo(800);
    }

    // --- the edges: values that are valid but easy to get wrong ---

    @Test
    void zeroPercentLeavesThePriceUnchanged() {
        assertThat(calc.applyPercentOff(1000, 0)).isEqualTo(1000);
    }

    @Test
    void hundredPercentMakesItFree() {
        assertThat(calc.applyPercentOff(1000, 100)).isZero();
    }

    // --- the unhappy path: bad input must be rejected ---

    @Test
    void junitWayOfAssertingAnException() {
        // assertThrows returns the caught exception so you can inspect it afterwards.
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> calc.applyPercentOff(1000, 150));

        assertThat(ex.getMessage()).contains("between 0 and 100");
    }

    @Test
    void assertjWayOfAssertingAnException() {
        // assertThatThrownBy reads as one fluent chain: type AND message in one place.
        assertThatThrownBy(() -> calc.applyPercentOff(1000, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    void negativePriceIsRejected() {
        assertThatThrownBy(() -> calc.applyPercentOff(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }
}
