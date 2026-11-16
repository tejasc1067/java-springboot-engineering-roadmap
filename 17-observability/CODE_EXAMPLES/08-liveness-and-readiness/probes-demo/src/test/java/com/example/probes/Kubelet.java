package com.example.probes;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny model of what the kubelet does with one container's probes, one simulated second at a time.
 * Rules it follows, the same as Kubernetes:
 * - a probe runs at initialDelaySeconds, then every periodSeconds (counted from the container's start);
 * - a probe fails if the endpoint says unhealthy OR takes longer than timeoutSeconds to answer;
 * - failureThreshold consecutive liveness failures restart the container (its clock starts again);
 * - failureThreshold consecutive readiness failures take the pod out of the Service; one success puts it back;
 * - while a startup probe is configured and hasn't passed yet, liveness and readiness don't run.
 * Not modelled: the growing back-off between restarts (CrashLoopBackOff), which makes real recovery slower.
 */
public final class Kubelet {

    public record Probe(int initialDelaySeconds, int periodSeconds, int timeoutSeconds, int failureThreshold) { }

    /** What the container answers when probed. {@code now} is cluster time, {@code uptime} the container's. */
    public interface Endpoint {
        Response probe(int now, int uptime);
    }

    /** {@code seconds} is how long the answer took. A refused connection is an instant failure. */
    public record Response(boolean healthy, double seconds) {
        public static Response ok() { return new Response(true, 0.01); }
        public static Response failed() { return new Response(false, 0.01); }
        public static Response refused() { return new Response(false, 0); }
    }

    private final Probe startup;
    private final Probe liveness;
    private final Probe readiness;
    private final Endpoint startupEndpoint;
    private final Endpoint livenessEndpoint;
    private final Endpoint readinessEndpoint;

    private int containerStartedAt;
    private boolean started;
    private boolean ready;
    private int startupFailures;
    private int livenessFailures;
    private int readinessFailures;
    private final List<Integer> restarts = new ArrayList<>();
    private final List<Integer> readySeconds = new ArrayList<>();

    public Kubelet(Probe startup, Endpoint startupEndpoint,
                   Probe liveness, Endpoint livenessEndpoint,
                   Probe readiness, Endpoint readinessEndpoint) {
        this.startup = startup;
        this.startupEndpoint = startupEndpoint;
        this.liveness = liveness;
        this.livenessEndpoint = livenessEndpoint;
        this.readiness = readiness;
        this.readinessEndpoint = readinessEndpoint;
        this.started = startup == null;
    }

    public static Kubelet withoutStartupProbe(Probe liveness, Endpoint livenessEndpoint,
                                              Probe readiness, Endpoint readinessEndpoint) {
        return new Kubelet(null, null, liveness, livenessEndpoint, readiness, readinessEndpoint);
    }

    public Kubelet run(int seconds) {
        for (int now = 0; now < seconds; now++) {
            tick(now);
            if (ready) readySeconds.add(now);
        }
        return this;
    }

    private void tick(int now) {
        int uptime = now - containerStartedAt;
        if (!started) {
            if (!due(startup, uptime)) return;
            if (passes(startup, startupEndpoint.probe(now, uptime))) {
                started = true;
            } else if (++startupFailures >= startup.failureThreshold()) {
                restart(now);
            }
            return;
        }
        if (due(liveness, uptime)) {
            if (passes(liveness, livenessEndpoint.probe(now, uptime))) {
                livenessFailures = 0;
            } else if (++livenessFailures >= liveness.failureThreshold()) {
                restart(now);
                return;
            }
        }
        if (due(readiness, uptime)) {
            if (passes(readiness, readinessEndpoint.probe(now, uptime))) {
                readinessFailures = 0;
                ready = true;
            } else if (++readinessFailures >= readiness.failureThreshold()) {
                ready = false;
            }
        }
    }

    private static boolean due(Probe probe, int uptime) {
        return uptime >= probe.initialDelaySeconds()
                && (uptime - probe.initialDelaySeconds()) % probe.periodSeconds() == 0;
    }

    private static boolean passes(Probe probe, Response response) {
        return response.healthy() && response.seconds() <= probe.timeoutSeconds();
    }

    private void restart(int now) {
        restarts.add(now);
        containerStartedAt = now + 1;
        started = startup == null;
        ready = false;
        startupFailures = livenessFailures = readinessFailures = 0;
    }

    public List<Integer> restarts() {
        return restarts;
    }

    public boolean wasReadyAt(int second) {
        return readySeconds.contains(second);
    }

    public int secondsReady() {
        return readySeconds.size();
    }
}
