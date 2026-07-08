package com.example.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves order-service boots on its own: its context wires its own orderdb and
 * builds the InventoryClient proxy. It does NOT need inventory-service running —
 * creating the client bean makes no network call. Actually calling inventory
 * requires it to be up; that end-to-end run is what topic 05's README walks through,
 * and topic 20 shows how to test the call with inventory stubbed.
 */
@SpringBootTest
class OrderServiceApplicationTests {

    @Autowired
    InventoryClient inventoryClient;

    @Autowired
    OrderRepository orders;

    @Test
    void contextLoadsWithItsOwnDatabaseAndClient() {
        assertThat(inventoryClient).isNotNull();
        assertThat(orders.count()).isZero();
    }
}
