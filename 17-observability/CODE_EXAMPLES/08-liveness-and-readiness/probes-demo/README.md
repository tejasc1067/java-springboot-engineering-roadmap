# probes-demo

What Spring Boot puts behind `/actuator/health/liveness` and `/readiness`, and the probe mistakes that restart
healthy pods. The probes are driven by `Kubelet`, a small model of the Kubernetes agent that runs them, so restart
counts can be asserted without a cluster.

- `ProbeEndpointsTest` — liveness has only `livenessState`; readiness has `readinessState` and the database.
  Database down: readiness 503, liveness 200. `REFUSING_TRAFFIC` and `BROKEN` published by the app.
- `StartupAndShutdownTest` — readiness is `REFUSING_TRAFFIC` while runners warm up; it flips back to
  `REFUSING_TRAFFIC` first thing at shutdown.
- `DatabaseInLivenessTest` (profile `broken-liveness`) — a 3-minute database outage restarts the pod at 80, 121,
  162 and 203 s.
- `DatabaseInReadinessTest` — the same outage: 0 restarts, out of the Service and back the moment the database is.
- `ReadinessGroupOverrideTest` (profile `readiness-without-state`) — `include: database` drops `readinessState`,
  so the probe stays `UP` while the app refuses traffic.
- `LivenessGroupOverrideTest` — configuring the liveness group without `include` puts every indicator, the
  database included, into liveness.
- `ProbeTimingTest` — a GC pause with `failureThreshold: 1`, a liveness endpoint that is slow under load, a
  90-second start with and without a startup probe.

Run (no Docker):

```
mvn -q test
```

15 tests, about 5 seconds.
