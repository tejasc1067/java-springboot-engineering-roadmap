package com.example.slo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ErrorBudgetTest {

    private final ErrorBudget slo = new ErrorBudget(0.999, Duration.ofDays(30));

    @Test
    void ninetyNinePointNine_over30Days_is43Point2MinutesOfDowntime() {
        assertThat(slo.allowedFailureRatio()).isCloseTo(0.001, within(1e-12));
        assertThat(slo.asDowntime()).isEqualTo(Duration.ofSeconds(43 * 60 + 12));
        assertThat(new ErrorBudget(0.99, Duration.ofDays(30)).asDowntime()).isEqualTo(Duration.ofHours(7).plusMinutes(12));
        assertThat(new ErrorBudget(0.9999, Duration.ofDays(30)).asDowntime()).isEqualTo(Duration.ofMillis(259_200));
    }

    @Test
    void burnRate_isTheErrorRatioDividedByTheBudget() {
        assertThat(slo.burnRate(0.001)).isCloseTo(1, within(1e-9));    // exactly on budget
        assertThat(slo.burnRate(0.05)).isCloseTo(50, within(1e-9));    // a bad deploy
        assertThat(slo.timeToExhaustion(50)).isEqualTo(Duration.ofMinutes(864));   // 14.4 hours
    }

    @Test
    void theFastBurnThreshold_14point4_spends2PercentOfTheBudgetInOneHour() {
        assertThat(slo.burnRateThatSpends(0.02, Duration.ofHours(1))).isCloseTo(14.4, within(1e-9));
        assertThat(slo.budgetSpent(14.4, Duration.ofHours(1))).isCloseTo(0.02, within(1e-9));
        assertThat(slo.timeToExhaustion(14.4)).isEqualTo(Duration.ofHours(50));
    }

    @Test
    void theSlowBurnThreshold_6_spends5PercentOfTheBudgetInSixHours() {
        assertThat(slo.burnRateThatSpends(0.05, Duration.ofHours(6))).isCloseTo(6, within(1e-9));
        assertThat(slo.timeToExhaustion(6)).isEqualTo(Duration.ofDays(5));
    }

    @Test
    void theTwoMinuteBlip_spendsTooLittleToPage() {
        // 600 failed requests at 1,000 requests a minute: the budget is 0.1% of 43.2 million requests.
        double monthlyRequests = 1_000.0 * 60 * 24 * 30;
        double budgetInRequests = monthlyRequests * slo.allowedFailureRatio();

        assertThat(budgetInRequests).isCloseTo(43_200, within(1e-6));
        assertThat(600 / budgetInRequests).isCloseTo(0.0139, within(0.0001));   // 1.4%, under the 2% fast-burn line
    }
}
