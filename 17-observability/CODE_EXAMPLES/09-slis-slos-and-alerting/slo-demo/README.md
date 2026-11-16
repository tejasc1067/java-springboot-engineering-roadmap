# slo-demo

Prometheus recording and alerting rules for two SLOs on a Spring Boot service, tested with `promtool`, plus the
error-budget arithmetic in Java.

- `rules/slo-rules.yml` — error ratio and slow-request ratio over 5m, 30m, 1h, 6h; multi-window burn-rate alerts
  (14.4× over 1h and 5m, 6× over 6h and 30m) for a 99.9% availability SLO and a 99%-under-300-ms latency SLO.
- `rules/slo-rules.test.yml` — six scenarios: healthy, bad deploy (fast burn), slow leak (slow burn only),
  2-minute blip (no page), recovery (fast burn clears first), slow requests (latency page).
- `rules/naive-rules.yml` + `naive-rules.test.yml` — `rate(5xx[1m]) > 0`, which pages on the blip.
- `ErrorBudget` + `ErrorBudgetTest` — 43.2 minutes a month, burn rates, where 14.4 and 6 come from.
- `MetricNamesTest` — the metric names and labels the rules use are the ones Spring Boot really exports,
  including the `le="0.3"` bucket from `management.metrics.distribution.slo.http.server.requests=300ms`.
- `NoSloBucketTest` — without that setting there are no `_bucket` series, so the latency rule matches nothing.

Run:

```
mvn -q test     # Java tests, no Docker
./demo.sh       # promtool checks; needs Docker and prom/prometheus:v3.5.0 (about 420 MB)
```

`demo.sh` runs `promtool` in a throwaway container with `rules/` mounted, prints its output, and a `PASS`/`FAIL`
line per check. It leaves nothing running.
