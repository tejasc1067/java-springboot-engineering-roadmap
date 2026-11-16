package com.example.availability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeriesAndParallelTest {

    static final int MINUTES = 2_000_000;   // about 4 years of minutes

    @Test
    void inSeries_theWholeIsLessAvailableThanItsWeakestPart() {
        // load balancer 99.99%, app 99.9%, database 99.95%
        double formula = Availability.series(0.9999, 0.999, 0.9995);

        assertThat(formula).isCloseTo(0.9984, within(0.0001));   // below every one of its parts
        assertThat(new FailureSimulation(1, MINUTES).series(0.9999, 0.999, 0.9995))
                .isCloseTo(formula, within(0.0002));
    }

    @Test
    void tenServicesAt999_inOneRequestPath_giveOnly99() {
        double[] tenServices = new double[10];
        java.util.Arrays.fill(tenServices, 0.999);

        assertThat(Availability.series(tenServices)).isCloseTo(0.990, within(0.0005));   // 99.0%: 87 hours a year
    }

    @Test
    void inParallel_independentCopiesMultiplyTheirDowntime() {
        assertThat(Availability.parallel(0.99, 0.99)).isCloseTo(0.9999, within(1e-9));
        assertThat(Availability.parallel(0.99, 0.99, 0.99)).isCloseTo(0.999999, within(1e-9));
        assertThat(new FailureSimulation(2, MINUTES).twoIndependentReplicas(0.99))
                .isCloseTo(0.9999, within(0.00005));
    }

    @Test
    void redundancyBehindASinglePointOfFailure_isCappedByThatPoint() {
        // Two app instances (99% each) behind one load balancer and one database (99.9% each).
        double system = Availability.series(0.999, Availability.parallel(0.99, 0.99), 0.999);

        assertThat(system).isCloseTo(0.9979, within(0.0001));   // can never beat 0.999 x 0.999 = 99.8%
    }
}
