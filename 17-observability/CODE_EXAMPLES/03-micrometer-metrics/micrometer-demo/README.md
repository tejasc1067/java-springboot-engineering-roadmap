# micrometer-demo

Micrometer's meter types, how they're exported to Prometheus, and the high-cardinality tag bug.

- `CounterAndGaugeTest` — counters only go up; gauges read the current value; a gauge on an object nobody keeps turns into `NaN` after GC; `strongReference(true)` fixes it.
- `TimerTest` — with `MockClock`: 100, 120 and 900 ms give count 3, max 900, mean 373 (a value no request had); a distribution summary.
- `PrometheusFormatTest` — `orders.placed` becomes `orders_placed_total`, timers export seconds; `publishPercentiles` gives one per-instance quantile; `publishPercentileHistogram` gives 69 bucket series for one timer.
- `CardinalityTest` — a `userId` tag: 10,000 users, 10,000 meters; a `plan` tag: 2 meters; `MeterFilter.maximumAllowableTags` caps it at 100 and drops the rest.
- `TimedDisabledTest` / `TimedEnabledTest` — `@Timed` records nothing until `management.observations.annotations.enabled=true` (with `spring-boot-starter-aop`).

```
mvn -q test
```

No Docker. Most tests use a plain `SimpleMeterRegistry` or `PrometheusMeterRegistry`, no Spring context.
