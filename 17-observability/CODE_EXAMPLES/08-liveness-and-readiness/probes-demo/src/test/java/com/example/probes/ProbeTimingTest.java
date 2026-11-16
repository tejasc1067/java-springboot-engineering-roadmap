package com.example.probes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.probes.Kubelet.Endpoint;
import com.example.probes.Kubelet.Probe;
import com.example.probes.Kubelet.Response;

/** Probe settings that restart a healthy app. Plain Java: only the kubelet's rules matter here. */
class ProbeTimingTest {

    private static final Probe READINESS = new Probe(0, 10, 1, 3);

    @Test
    void oneGcPause_withFailureThresholdOne_restartsTheContainer() {
        // A 3-second stop-the-world pause at 01:40. The JVM is fine before and after it.
        Endpoint pauseAt100 = (now, uptime) -> now >= 100 && now < 103 ? new Response(true, 3.0) : Response.ok();

        Kubelet impatient = Kubelet.withoutStartupProbe(new Probe(10, 10, 1, 1), pauseAt100, READINESS, pauseAt100).run(300);
        Kubelet tolerant = Kubelet.withoutStartupProbe(new Probe(10, 10, 1, 3), pauseAt100, READINESS, pauseAt100).run(300);

        assertThat(impatient.restarts()).containsExactly(100);   // a timeout is a failure
        assertThat(tolerant.restarts()).isEmpty();               // one slow answer out of three is tolerated
    }

    @Test
    void aLivenessEndpointThatSlowsUnderLoad_restartsThePodExactlyWhenItIsBusiest() {
        // From 01:00 traffic peaks and the endpoint takes 1.5 s, because it waits for a busy thread pool.
        Endpoint slowUnderLoad = (now, uptime) -> now >= 60 ? new Response(true, 1.5) : Response.ok();

        Kubelet kubelet = Kubelet.withoutStartupProbe(new Probe(0, 10, 1, 3), slowUnderLoad, READINESS, slowUnderLoad).run(300);

        // The answers are all "UP", just late. Each restart throws away the in-flight requests and puts
        // more load on the remaining pods.
        assertThat(kubelet.restarts()).containsExactly(80, 101, 122, 143, 164, 185, 206, 227, 248, 269, 290);
    }

    @Test
    void aSlowStartingApp_withoutAStartupProbe_isKilledBeforeItEverStarts() {
        // The app needs 90 s before it answers at all (large cache, migrations, a slow first connection).
        Endpoint slowStart = (now, uptime) -> uptime < 90 ? Response.refused() : Response.ok();

        Kubelet kubelet = Kubelet.withoutStartupProbe(new Probe(10, 10, 1, 3), slowStart, READINESS, slowStart).run(600);

        assertThat(kubelet.restarts()).hasSize(19);      // killed every 31 seconds, forever
        assertThat(kubelet.secondsReady()).isZero();     // never served a request
    }

    @Test
    void aStartupProbe_givesTheAppTimeToStart_thenLivenessTakesOver() {
        Endpoint slowStart = (now, uptime) -> uptime < 90 ? Response.refused() : Response.ok();
        Probe startup = new Probe(0, 10, 1, 30);         // up to 300 s to start; liveness waits until it passes

        Kubelet kubelet = new Kubelet(startup, slowStart, new Probe(0, 10, 1, 3), slowStart, READINESS, slowStart).run(600);

        assertThat(kubelet.restarts()).isEmpty();
        assertThat(kubelet.wasReadyAt(89)).isFalse();
        assertThat(kubelet.wasReadyAt(100)).isTrue();
    }
}
