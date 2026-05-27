package com.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These tests are written in the order the markdown describes. Each one was RED
 * (failing, or not even compiling) before the matching production code existed.
 */
class ShippingFeeCalculatorTest {

    private final ShippingFeeCalculator calc = new ShippingFeeCalculator();

    // Cycle 1: the first behavior - a small order pays the flat fee.
    @Test
    void chargesFlatFeeForSmallOrders() {
        assertThat(calc.feeFor(1000)).isEqualTo(599);
    }

    // Cycle 2: large orders ship free.
    @Test
    void shipsFreeWellAboveTheThreshold() {
        assertThat(calc.feeFor(9999)).isZero();
    }

    // Cycle 3: pin the boundary. Is 5000 free or not? We decide: free at exactly 5000.
    @Test
    void thresholdIsInclusiveAtExactly5000() {
        assertThat(calc.feeFor(4999)).isEqualTo(599);   // just below: still charged
        assertThat(calc.feeFor(5000)).isZero();         // exactly at: free
    }

    // Cycle 4: reject nonsense input.
    @Test
    void rejectsNegativeTotal() {
        assertThatThrownBy(() -> calc.feeFor(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }
}
