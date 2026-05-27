package com.example;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DiscountCalculatorParameterizedTest {

    private final DiscountCalculator calc = new DiscountCalculator();

    // @ValueSource: one parameter, a list of simple values. The test runs once per value.
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 99, 100})
    void everyPercentInRangeIsAccepted(int percent) {
        long result = calc.applyPercentOff(1000, percent);
        assertThat(result).isBetween(0L, 1000L);
    }

    // @CsvSource: several parameters per case, written as comma-separated rows.
    @ParameterizedTest
    @CsvSource({
            "1000,   0, 1000",
            "1000,  20,  800",
            "1000, 100,    0",
            " 999,  10,  900"   // integer division: 999*10/100 = 99, so 999 - 99 = 900
    })
    void discountIsComputedCorrectly(long price, int percent, long expected) {
        assertThat(calc.applyPercentOff(price, percent)).isEqualTo(expected);
    }

    // @MethodSource: when the inputs are richer than CSV can express, supply them from a method.
    static Stream<Arguments> priceScenarios() {
        return Stream.of(
                arguments(2500L, 50, 1250L),
                arguments(100L, 99, 1L),
                arguments(0L, 50, 0L)
        );
    }

    @ParameterizedTest
    @MethodSource("priceScenarios")
    void discountFromAMethodSource(long price, int percent, long expected) {
        assertThat(calc.applyPercentOff(price, percent)).isEqualTo(expected);
    }

    // @EnumSource: run once per chosen enum constant.
    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"NEW", "PAID"})
    void newAndPaidOrdersCanBeCancelled(OrderStatus status) {
        assertThat(status.canBeCancelled()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "CANCELLED"})
    void shippedAndCancelledOrdersCannotBeCancelled(OrderStatus status) {
        assertThat(status.canBeCancelled()).isFalse();
    }
}
