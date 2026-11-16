package com.example.availability;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NinesTest {

    @Test
    void eachExtraNine_cutsAllowedDowntimeTenfold() {
        assertThat(Availability.downtimePerYear(0.99)).isEqualTo(Duration.ofHours(87).plusMinutes(36));        // 3.65 days
        assertThat(Availability.downtimePerYear(0.999)).isEqualTo(Duration.ofHours(8).plusMinutes(45).plusSeconds(36));
        assertThat(Availability.downtimePerYear(0.9999)).isEqualTo(Duration.ofMinutes(52).plusSeconds(33).plusMillis(600));
        assertThat(Availability.downtimePerYear(0.99999)).isEqualTo(Duration.ofMinutes(5).plusSeconds(15).plusMillis(360));
    }

    @Test
    void perMonth() {
        assertThat(Availability.downtimePerMonth(0.99)).isEqualTo(Duration.ofHours(7).plusMinutes(18));
        assertThat(Availability.downtimePerMonth(0.999)).isEqualTo(Duration.ofMinutes(43).plusSeconds(48));   // one deploy gone wrong
        assertThat(Availability.downtimePerMonth(0.9999)).isEqualTo(Duration.ofMinutes(4).plusSeconds(22).plusMillis(800));
    }

    @Test
    void repairingFaster_isWorthAsMuchAsFailingLess() {
        // Fails once a month (30 days), takes 1 hour to notice and fix.
        double slowRepair = Availability.fromMtbfAndMttr(Duration.ofDays(30), Duration.ofHours(1));
        // Same failures, fixed in 6 minutes (an alert, a rollback button).
        double fastRepair = Availability.fromMtbfAndMttr(Duration.ofDays(30), Duration.ofMinutes(6));
        // Fails ten times less often, still takes an hour.
        double rareFailure = Availability.fromMtbfAndMttr(Duration.ofDays(300), Duration.ofHours(1));

        assertThat(slowRepair).isBetween(0.9986, 0.9987);   // 99.86%: misses a 99.9% target
        assertThat(fastRepair).isBetween(0.99986, 0.99987);
        assertThat(rareFailure).isBetween(0.99986, 0.99987);
    }
}
