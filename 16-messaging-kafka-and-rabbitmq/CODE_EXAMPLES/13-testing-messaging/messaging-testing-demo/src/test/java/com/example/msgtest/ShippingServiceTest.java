package com.example.msgtest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The handler logic, tested with no broker, no Spring, in milliseconds. Most messaging tests should look like this. */
class ShippingServiceTest {

    private final ShippingService shipping = new ShippingService();

    @Test
    void validOrder_isShipped() {
        assertThat(shipping.handle("order-1:2500")).contains("ship-order-1");
        assertThat(shipping.shipmentFor("order-1")).contains("ship-order-1");
    }

    @Test
    void invalidOrders_areNotShipped() {
        assertThat(shipping.handle("order-1:-5")).isEmpty();
        assertThat(shipping.handle("order-1:abc")).isEmpty();
        assertThat(shipping.handle("garbage")).isEmpty();
        assertThat(shipping.shipmentFor("order-1")).isEmpty();
    }

    @Test
    void theSameEventTwice_shipsOnce() {
        assertThat(shipping.handle("order-1:2500")).isPresent();
        assertThat(shipping.handle("order-1:2500")).isEmpty();
    }
}
