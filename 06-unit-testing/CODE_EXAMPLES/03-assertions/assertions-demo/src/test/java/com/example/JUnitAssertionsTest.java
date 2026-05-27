package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The assertions that ship with JUnit. No extra library needed. */
class JUnitAssertionsTest {

    @Test
    void theCommonBuiltInAssertions() {
        Order order = new Order();
        order.addItem(new OrderItem("BOOK", 2, 1500));
        order.addItem(new OrderItem("PEN", 5, 100));

        assertEquals(3500, order.totalCents());   // 2*1500 + 5*100
        assertFalse(order.isEmpty());
        assertEquals(2, order.items().size());
        assertTrue(order.totalCents() > 0);
        assertNotNull(order.items());
    }

    @Test
    void assertAllReportsEveryFailureTogether() {
        OrderItem item = new OrderItem("BOOK", 2, 1500);

        // Without assertAll, the first failing assertion stops the test and hides the rest.
        // assertAll runs all of them and reports every failure at once.
        assertAll(
                () -> assertEquals("BOOK", item.sku()),
                () -> assertEquals(2, item.quantity()),
                () -> assertEquals(3000, item.lineTotalCents())
        );
    }
}
