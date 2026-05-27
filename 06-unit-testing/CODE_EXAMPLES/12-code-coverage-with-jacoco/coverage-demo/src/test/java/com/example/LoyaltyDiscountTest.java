package com.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoyaltyDiscountTest {

    private final LoyaltyDiscount policy = new LoyaltyDiscount();

    @Test
    void goldGetsTwentyPercentOnLargeOrders() {
        assertThat(policy.percentFor("GOLD", 10000)).isEqualTo(20);
    }

    @Test
    void goldGetsFifteenPercentOnSmallOrders() {
        assertThat(policy.percentFor("GOLD", 5000)).isEqualTo(15);
    }

    @Test
    void unknownTierGetsNoDiscount() {
        assertThat(policy.percentFor("BRONZE", 9999)).isZero();
    }

    // Deliberately MISSING: a test for the SILVER tier.
    // After `mvn test`, open target/site/jacoco/index.html and you'll see the
    // SILVER branch highlighted as uncovered. The "Try this yourself" closes the gap.
}
