# Common mistakes — Observability

Real observability bugs, each one reproduced by a test or `demo.sh` in this module. They share one trait: nothing fails when they're introduced. The app runs, the dashboard has lines on it, and the gap only shows during an incident, when the trace is missing, the log line has no id, the graph says everything is fine, or the alert fires for nothing (or doesn't fire at all).

---

## 1. `MDC.put` without `MDC.remove`

```java
MDC.put("userId", userId);
log.info("handling {}", path);
```

**What goes wrong:** server threads are pooled, so the next request on the same thread inherits the value. An anonymous request is logged as the previous user (topic 02's `MdcLeakTest`). Searching logs by user now returns other people's requests.

**Fix:** `try { ... } finally { MDC.remove("userId"); }`, or `try (var c = MDC.putCloseable("userId", userId)) { ... }`. Better still, put request context in MDC in one filter that always cleans up.

---

## 2. Background threads with no context

```java
MDC.put("requestId", id);
receipts.sendReceipt(orderId);                                     // @Async
CompletableFuture.runAsync(() -> log.info("emailing"), executor);  // plain ThreadPoolTaskExecutor
```

**What goes wrong:** MDC and the current trace are per thread. Work on `task-N` or a pool thread logs with an empty trace id and no `requestId`, and its HTTP calls start new traces, so the background half of the request can't be found (topic 02's `MdcAsyncTest`, topic 06's `AsyncNoPropagationTest`, topic 07's `LogCorrelationTest`).

**Fix:** a `ContextPropagatingTaskDecorator` bean (Boot applies a `TaskDecorator` bean to the `@Async` executor), and `setTaskDecorator(...)` on executors you create yourself. Your own `MDC.put` keys don't travel even then: use baggage with `management.tracing.baggage.correlation.fields` for values that must follow the request, or a decorator that copies the MDC (topic 02's `MdcPropagationConfig`).

---

## 3. An id as a metric tag

```java
Counter.builder("orders.placed").tag("userId", userId).register(registry).increment();
```

**What goes wrong:** every distinct value creates a time series that lives in the app's memory and in Prometheus. 10,000 users gave 10,000 meters (topic 03's `CardinalityTest`). Memory, scrape size, query speed, and the monitoring bill grow with traffic. Raw URLs, order ids, and exception messages do the same; a `RestTemplate` or `TestRestTemplate` called with a finished URL string tags client metrics with every real path (topic 04).

**Fix:** tag only with small, fixed value sets (`plan`, `status`, `outcome`) and put ids in logs and span attributes. A `MeterFilter.maximumAllowableTags` bean is a safety net: it caps the series count, but silently drops the data beyond the cap.

---

## 4. A gauge on an object nobody else keeps

```java
registry.gauge("pending.orders", new ArrayList<>(orders), List::size);
```

**What goes wrong:** Micrometer holds a gauged object through a weak reference. After a garbage collection the gauge reports `NaN` forever, with no error (topic 03's `CounterAndGaugeTest`).

**Fix:** gauge a field of a long-lived bean, or use `Gauge.builder(...).strongReference(true)`.

---

## 5. Averaging percentiles across instances

```promql
avg(histogram_quantile(0.95, sum by (pod, le) (rate(checkout_seconds_bucket[5m]))))
```

**What goes wrong:** the average ignores how much traffic each instance served. In topic 04's `PercentileAveragingTest`, one pod serves 1,000 fast requests and another 100 with 10 slow ones: the query reports 0.88 s, but the real p95 is 0.0096 s. It can just as easily hide one slow pod.

**Fix:** sum the buckets across instances, keeping `le`, then take one quantile: `histogram_quantile(0.95, sum by (le) (rate(checkout_seconds_bucket[5m])))`. Publish histograms (`percentiles-histogram`), not client-side percentiles, which can't be combined at all.

---

## 6. `rate()` on a gauge, or arithmetic on raw counters

```promql
increase(queue_depth[5m])            # a gauge
jobs_total - jobs_total offset 1h    # a counter, across a deploy
```

**What goes wrong:** `rate` and `increase` treat every drop as a counter reset, so a queue going 10, 50, 20 shows an increase of 60 or more; Prometheus 3 flags it with "metric might not be a counter" (topic 04's `RateOnGaugeTest`). Subtracting counter values across a restart gives negative work: -80 jobs in `CounterResetTest`.

**Fix:** use `rate`/`increase` only on `_total`, `_count`, `_sum`, and `_bucket`, and always *before* `sum`: `sum(rate(x[5m]))`, never `rate(sum(x))`. For gauges use the value itself, `max_over_time`, `delta`, or `deriv`.

---

## 7. Average latency and error counts on the dashboard

```promql
sum(rate(http_server_requests_seconds_sum[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
sum(http_server_requests_seconds_count{status="500"})
```

**What goes wrong:** with 5% of checkouts taking 1.5 s, the average shows about 0.1 s while p99 is 1.7 s (topic 05's `demo.sh`). "Errors: 5" means nothing without the total. Neither is split by endpoint, and both include Prometheus's own scrapes of `/actuator/prometheus`.

**Fix:** p95/p99 from `sum by (le, uri)`, errors as a ratio (`rate` of 5xx over `rate` of all), `uri!~"/actuator.*"` in every query, and a lint test over the dashboard JSON (topic 05's `DashboardLintTest`).

---

## 8. An HTTP client made by hand

```java
RestClient client = RestClient.create();   // or new RestTemplate(), WebClient.create()
```

**What goes wrong:** no `traceparent` header is sent and no client span is recorded. The downstream starts its own trace, so one request appears as two unrelated traces, and the slow call is invisible from the caller's side. No error (topic 06's `PropagationTest`).

**Fix:** build every client from the injected `RestClient.Builder`, `RestTemplateBuilder`, or `WebClient.Builder`.

---

## 9. Not knowing the 10% sampling default

```yaml
# management.tracing.sampling.probability not set
```

**What goes wrong:** Spring Boot samples 10% of traces by default. Nine requests in ten are never exported, the one you're debugging isn't in Jaeger, and "tracing is broken". An upstream "not sampled" decision (`traceparent` flags `00`) is followed too, whatever your own setting (topic 06's `SamplingTest`; topic 01 counts 200 requests in the timer and about 20 traces).

**Fix:** set the probability on purpose: 1.0 in development and tests, a budgeted value in production, or tail sampling ("keep all errors and slow traces") in an OpenTelemetry Collector.

---

## 10. Letting the server log your unhandled exceptions

```java
throw new IllegalStateException("database unavailable");   // no @ExceptionHandler for it
```

**What goes wrong:** Tomcat logs the stack trace after the request's span has closed, so the ERROR line has an empty trace id. Searching by trace id finds every line of the request except the error. Boot's default error body has no id for the customer to quote either (topic 07's `LogCorrelationTest`).

**Fix:** a lowest-precedence `@ExceptionHandler(Exception.class)` that logs the error itself and returns a `ProblemDetail` with a `traceId` property (topic 07's `CatchAllErrorTest`).

---

## 11. The database in the liveness check

```yaml
management.endpoint.health.group.liveness.include: livenessState, db
```

**What goes wrong:** a database outage fails liveness on every pod at once. Kubernetes restarts them, the new containers fail again, and so on: 4 restarts in a 3-minute outage, each dropping in-flight requests. Restarting can't fix a database (topic 08's `DatabaseInLivenessTest`).

**Fix:** liveness is `livenessState` only. Put dependencies this instance needs into readiness: 0 restarts, and back in the Service the moment the database returns (`DatabaseInReadinessTest`).

---

## 12. Configuring a probe group and losing its state indicator

```yaml
management.endpoint.health.group.readiness.include: db, redis
management.endpoint.health.group.liveness.show-components: always
```

**What goes wrong:** `include` replaces the group's members, so readiness loses `readinessState` and stays UP during warm-up and shutdown, while the app is refusing traffic (topic 08's `ReadinessGroupOverrideTest`). Configuring the liveness group without `include` turns it into *every* indicator, the database included (`LivenessGroupOverrideTest`). Neither gives an error.

**Fix:** whenever you configure a probe group, write its whole member list: `readinessState, db, redis` and `livenessState`.

---

## 13. A slow-starting app with no startup probe

```yaml
livenessProbe: { httpGet: { path: /actuator/health/liveness, port: 8080 }, initialDelaySeconds: 10, periodSeconds: 10, failureThreshold: 3 }
```

**What goes wrong:** an app that needs 90 s to start is killed at about 31 s, every time: 19 restarts in 10 minutes and never ready (topic 08's `ProbeTimingTest`). A 1-second timeout with `failureThreshold: 1` kills a healthy pod during one GC pause.

**Fix:** a `startupProbe` whose `failureThreshold × periodSeconds` covers the slowest start, and a liveness probe with a threshold of at least 3 and a timeout with headroom.

---

## 14. Paging on any error, or on CPU

```yaml
- alert: OrdersAnyServerError
  expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) > 0
```

**What goes wrong:** it pages for a 2-minute blip that fixed itself (topic 09's `naive-rules.test.yml`). CPU alerts page while users are fine. People learn to ignore the channel, and then miss the real alert.

**Fix:** alert on the SLO's burn rate with two windows each: 14.4× over 1 h *and* 5 m, 6× over 6 h *and* 30 m. Test the rules with `promtool test rules` against healthy, blip, fast-burn, and slow-burn series (topic 09's `slo-rules.test.yml`).

---

## 15. An alert or test built on a series that doesn't exist

```yaml
expr: sum(rate(http_server_requests_seconds_bucket{le="0.3"}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
```

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)   // GET /actuator/prometheus -> 404
```

**What goes wrong:** Spring Boot exports no `_bucket` series unless configured, and with `percentiles-histogram` the nearest boundaries to 300 ms are 0.268 and 0.358. The rule returns no data, and no data never fires (topic 09's `NoSloBucketTest`). In tests the same blindness comes from Boot switching metrics export off: `/actuator/prometheus` doesn't exist, and a test that tolerates the 404 proves nothing (topic 04's `ExpositionFormatTest`).

**Fix:** `management.metrics.distribution.slo.http.server.requests=300ms` to get an exact bucket, a test that checks the series names your rules use (`MetricNamesTest`), `absent(...)` alerts for critical series, and `@AutoConfigureObservability` on tests that read metrics or spans.
