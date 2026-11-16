# three-signals-demo

One slow downstream call (pricing-service takes 200 ms), seen through each of the three signals.

- `ThreeSignalsTest`
  - `log_tellsYouThatThisOneRequestWasSlow_butNotWhy` — the log line for `order-42` says `tookMs=214`, nothing more.
  - `metrics_tellYouHowManyAndHowSlow_acrossAllRequests_butNotWhichOne` — `checkout` timer: count 5, max ≥ 200 ms, mean much lower; the `pricing.call` timer is just as slow.
  - `trace_showsWhereTheTimeWent_insideThisOneRequest` — span `pricing.call` is a child of `checkout` and takes 90%+ of it.
- `SamplingTest` — with Spring Boot's default `management.tracing.sampling.probability=0.1`, 200 checkouts give a timer count of 200 and about 20 traces.

Spans go to an `InMemorySpanExporter` (from `opentelemetry-sdk-testing`) instead of a tracing backend, so no Docker is needed.

```
mvn -q test
```

The tests print a few hundred log lines; that's the log signal.
