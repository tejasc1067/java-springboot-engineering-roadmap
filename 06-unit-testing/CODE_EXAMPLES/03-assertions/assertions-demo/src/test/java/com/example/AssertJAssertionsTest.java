package com.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same checks as JUnitAssertionsTest, written with AssertJ.
 * One static import (assertThat) and then everything reads left to right.
 */
class AssertJAssertionsTest {

    @Test
    void fluentVersionOfTheCommonChecks() {
        Order order = new Order();
        order.addItem(new OrderItem("BOOK", 2, 1500));
        order.addItem(new OrderItem("PEN", 5, 100));

        assertThat(order.totalCents()).isEqualTo(3500);
        assertThat(order.isEmpty()).isFalse();
        assertThat(order.items()).hasSize(2);
        assertThat(order.totalCents()).isPositive();
    }

    @Test
    void assertingOnCollectionsIsWhereAssertJEarnsItsKeep() {
        Order order = new Order();
        order.addItem(new OrderItem("BOOK", 2, 1500));
        order.addItem(new OrderItem("PEN", 5, 100));

        // Pull one field out of every element, then assert on the resulting list.
        // Doing this with JUnit assertions takes a loop and several lines.
        assertThat(order.items())
                .hasSize(2)
                .extracting(OrderItem::sku)
                .containsExactly("BOOK", "PEN");
    }
}
