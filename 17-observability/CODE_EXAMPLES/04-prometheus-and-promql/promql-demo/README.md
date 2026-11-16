# promql-demo

A real Prometheus scraping a real Spring Boot app every second, and PromQL queries that return confident
wrong numbers next to the right ones.

- `ExpositionFormatTest` (no Docker): what `/actuator/prometheus` returns. `_total` on counters, cumulative
  `le` buckets ending in `+Inf` = `_count`, URI templates instead of real paths.
- `PromqlBasicsTest`: raw counter (a total since start) vs `rate()`/`increase()`, `sum by`, instant vs range vectors.
- `PercentileAveragingTest`: averaging two pods' p95s gives 0.88 s; the real p95 is 0.0096 s.
- `RateOnGaugeTest`: `increase()` on a gauge invents growth; Prometheus 3 says "metric might not be a counter".
- `CounterResetTest`: across a restart, "last minus first" is -80 jobs; `increase()` is at least 220.

`PrometheusEnvironment` starts the app on a random port, a `FakeTarget` whose values the tests set by hand,
and `prom/prometheus:v3.5.0` (Testcontainers) scraping both via `host.testcontainers.internal`. The tests
query Prometheus's HTTP API (`/api/v1/query`) and wait for scrapes with Awaitility.

Run (Docker running):

```
mvn -q test
```

First run pulls `prom/prometheus:v3.5.0` (about 420 MB on disk). After that the suite takes about 20 seconds.
