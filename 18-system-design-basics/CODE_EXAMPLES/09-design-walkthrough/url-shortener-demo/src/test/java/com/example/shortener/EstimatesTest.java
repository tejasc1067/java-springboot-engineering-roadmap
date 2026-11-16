package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** The back-of-envelope numbers from the topic, computed instead of hand-waved. */
class EstimatesTest {

    static final double SECONDS_PER_MONTH = 30 * 24 * 3600;     // 2,592,000
    static final long NEW_URLS_PER_MONTH = 100_000_000L;
    static final int READS_PER_WRITE = 100;
    static final int PEAK_FACTOR = 5;
    static final int BYTES_PER_ROW = 500;                        // code + URL + timestamps + index overhead

    @Test
    void trafficPerSecond() {
        double writesPerSecond = NEW_URLS_PER_MONTH / SECONDS_PER_MONTH;
        double readsPerSecond = writesPerSecond * READS_PER_WRITE;

        assertThat(writesPerSecond).isCloseTo(38.6, within(0.1));
        assertThat(readsPerSecond).isCloseTo(3_858, within(1.0));
        assertThat(readsPerSecond * PEAK_FACTOR).isCloseTo(19_290, within(5.0));
    }

    @Test
    void storageOverFiveYears() {
        long urls = NEW_URLS_PER_MONTH * 12 * 5;
        assertThat(urls).isEqualTo(6_000_000_000L);
        assertThat(urls * BYTES_PER_ROW).isEqualTo(3_000_000_000_000L);          // 3 TB
    }

    @Test
    void cacheForTheHottestTwentyPercentOfADaysRedirects() {
        double redirectsPerDay = NEW_URLS_PER_MONTH * READS_PER_WRITE / 30.0;   // about 333 million
        double cacheBytes = redirectsPerDay * 0.20 * BYTES_PER_ROW;
        assertThat(cacheBytes / 1e9).isCloseTo(33.3, within(0.1));              // GB: fits in memory
    }

    @Test
    void codeLength() {
        assertThat(Base62.capacity(6)).isEqualTo(56_800_235_584L);
        assertThat(Base62.capacity(7)).isEqualTo(3_521_614_606_208L);

        // Random codes: chance that one new code hits an existing one, once 6 billion exist.
        double taken = 6e9;
        assertThat(taken / Base62.capacity(6)).isCloseTo(0.106, within(0.001));     // ~1 in 10 inserts retries
        assertThat(taken / Base62.capacity(7)).isCloseTo(0.0017, within(0.0001));   // ~1 in 600
    }

    @Test
    void truncatedHash_birthdayBound() {
        double codes = Base62.capacity(7);
        // About 1.18 * sqrt(N) items give a 50% chance that at least two share a value.
        double fiftyPercentAt = Math.sqrt(2 * codes * Math.log(2));
        assertThat(fiftyPercentAt).isCloseTo(2_209_591, within(100.0));
        // At 100M URLs a month that's under a day of traffic.
        assertThat(fiftyPercentAt / (NEW_URLS_PER_MONTH / 30.0) * 24).isCloseTo(15.9, within(0.1)); // hours

        // Expected colliding pairs after 5 years: n^2 / 2N.
        double n = 6e9;
        assertThat(n * n / (2 * codes)).isCloseTo(5_111_266, within(1_000.0));
    }
}
