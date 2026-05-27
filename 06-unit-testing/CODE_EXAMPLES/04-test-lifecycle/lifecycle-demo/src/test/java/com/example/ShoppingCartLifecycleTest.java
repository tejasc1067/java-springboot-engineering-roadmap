package com.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingCartLifecycleTest {

    private ShoppingCart cart;

    // An instance field. If JUnit reused one test instance for every method,
    // this counter would climb across tests. It doesn't - see the assertions.
    private int beforeEachRuns = 0;

    @BeforeAll
    static void runsOnceBeforeEverything() {
        // Runs a single time, before any test in this class. Must be static
        // (in the default lifecycle) because there's no instance yet.
        System.out.println("[BeforeAll] one-time setup for the whole class");
    }

    @BeforeEach
    void freshCartBeforeEachTest() {
        // Runs before EACH test. This is where you build a clean starting state,
        // so no test depends on what a previous test left behind.
        cart = new ShoppingCart();
        beforeEachRuns++;
    }

    @AfterEach
    void runsAfterEachTest() {
        System.out.println("[AfterEach] test finished; cart held " + cart.size() + " item(s)");
    }

    @AfterAll
    static void runsOnceAfterEverything() {
        System.out.println("[AfterAll] one-time teardown for the whole class");
    }

    @Test
    void addingOneItem() {
        cart.add("BOOK");

        assertThat(cart.size()).isEqualTo(1);
        // Fresh instance per test => the counter is 1, not "however many tests ran before".
        assertThat(beforeEachRuns).isEqualTo(1);
    }

    @Test
    void addingTwoItems() {
        cart.add("BOOK");
        cart.add("PEN");

        // If the two tests shared one cart, the "BOOK" from the other test might
        // still be here and this would be 3. It's 2, which proves isolation.
        assertThat(cart.size()).isEqualTo(2);
        assertThat(beforeEachRuns).isEqualTo(1);
    }
}
