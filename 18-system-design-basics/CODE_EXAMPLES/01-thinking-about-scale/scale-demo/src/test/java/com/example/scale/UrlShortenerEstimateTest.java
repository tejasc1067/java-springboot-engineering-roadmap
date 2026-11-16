package com.example.scale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The worked example from the topic: a URL shortener with 10 million daily active users.
 * Assumptions (write yours down the same way, they matter more than the arithmetic):
 *   - each user creates a short link every 10 days  -> 1,000,000 new links a day
 *   - each user clicks 10 short links a day          -> 100,000,000 redirects a day
 *   - a stored link is about 500 bytes (long URL, code, owner, timestamps, index overhead)
 *   - a redirect response is about 500 bytes
 *   - peak traffic is 3x the daily average
 */
class UrlShortenerEstimateTest {

    static final double DAU = 10_000_000;
    static final double WRITES_PER_DAY = DAU / 10;
    static final double READS_PER_DAY = DAU * 10;

    @Test
    void readsOutnumberWrites_100to1() {
        assertThat(READS_PER_DAY / WRITES_PER_DAY).isEqualTo(100);
    }

    @Test
    void requestsPerSecond_averageAndPeak() {
        double writes = Estimate.perSecond(WRITES_PER_DAY);
        double reads = Estimate.perSecond(READS_PER_DAY);

        assertThat(writes).isCloseTo(11.6, within(0.1));     // a laptop could do this
        assertThat(reads).isCloseTo(1_157, within(1.0));
        assertThat(Estimate.peak(reads, 3)).isCloseTo(3_472, within(1.0));
    }

    @Test
    void storageGrowsAbout180GigabytesAYear() {
        double bytes = Estimate.bytesPerYear(WRITES_PER_DAY, 500);

        assertThat(bytes / 1e9).isCloseTo(182.5, within(0.1));
        assertThat(bytes * 5 / 1e12).isCloseTo(0.9, within(0.1));   // under a terabyte in 5 years: one database holds it
    }

    @Test
    void peakBandwidthIsUnder2MegabytesASecond() {
        double peakReads = Estimate.peak(Estimate.perSecond(READS_PER_DAY), 3);

        assertThat(Estimate.bytesPerSecond(peakReads, 500) / 1e6).isCloseTo(1.74, within(0.01));
    }

    @Test
    void instances_fromPerInstanceCapacity_withHeadroomAndASpare() {
        double peakReads = Estimate.peak(Estimate.perSecond(READS_PER_DAY), 3);

        // Load-tested: one instance serves 1,000 redirects/s. Run at 70% so a spike doesn't tip it over,
        // plus one spare so a deploy or a crash doesn't overload the others.
        assertThat(Estimate.instances(peakReads, 1_000, 0.7, 1)).isEqualTo(6);
        assertThat(Estimate.instances(peakReads, 1_000, 1.0, 0)).isEqualTo(4);   // the "100% busy, nothing ever fails" fantasy
    }

    @Test
    void littlesLaw_sizesThreadAndConnectionPools() {
        // One instance at its 70% target: 700 requests/s, 20 ms each, of which 5 ms holds a database connection.
        assertThat(Estimate.inFlight(700, 0.020)).isCloseTo(14, within(0.001));   // requests in flight -> threads busy
        assertThat(Estimate.inFlight(700, 0.005)).isCloseTo(3.5, within(0.001));  // connections busy

        // The same instance when the database slows to 200 ms per query:
        assertThat(Estimate.inFlight(700, 0.215)).isCloseTo(150.5, within(0.001)); // 150 threads busy: Tomcat's default max is 200
    }
}
