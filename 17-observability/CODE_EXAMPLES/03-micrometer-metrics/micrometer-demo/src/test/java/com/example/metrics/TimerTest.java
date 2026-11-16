package com.example.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** MockClock: time moves only when the test says so, so durations are exact. */
class TimerTest {

    private final MockClock clock = new MockClock();
    private final MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

    @Test
    void aTimerRecordsCountTotalAndMax() {
        Timer checkout = Timer.builder("checkout").register(registry);

        for (long ms : new long[] {100, 120, 900}) {
            Timer.Sample sample = Timer.start(clock);
            clock.add(Duration.ofMillis(ms));   // the work
            sample.stop(checkout);
        }

        assertThat(checkout.count()).isEqualTo(3);
        assertThat(checkout.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1120);
        assertThat(checkout.mean(TimeUnit.MILLISECONDS)).isCloseTo(373.3, org.assertj.core.data.Offset.offset(0.1));
        assertThat(checkout.max(TimeUnit.MILLISECONDS)).isEqualTo(900);
        // The mean (373 ms) describes nobody: two requests took ~110 ms, one took 900 ms.
    }

    @Test
    void aDistributionSummaryIsATimerForThingsThatArentTime() {
        DistributionSummary payload = DistributionSummary.builder("http.request.body.size").baseUnit("bytes")
                .register(registry);
        payload.record(512);
        payload.record(2048);

        assertThat(payload.count()).isEqualTo(2);
        assertThat(payload.totalAmount()).isEqualTo(2560);
        assertThat(payload.max()).isEqualTo(2048);
    }
}
