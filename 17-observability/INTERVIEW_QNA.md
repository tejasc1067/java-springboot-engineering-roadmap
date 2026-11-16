# Interview questions — Observability

Each answer is what an interviewer hopes to hear: the mechanism, the trade-off, and the production gotcha. The behaviour described comes from this module's tests (Spring Boot 3.5, Micrometer, OpenTelemetry, Prometheus 3.5).

---

## 1. Logs, metrics, and traces: what does each answer, and how do you use them together in an incident?

**Logs** are individual events with any detail you like, ids included. They answer "what happened to this request?" Their cost grows with traffic times lines per request.

**Metrics** are numbers aggregated over time: counts, sums, maxima, histogram buckets. They answer "how many, how often, how slow, across all requests?" Their cost depends on the number of time series, not on traffic, which is why they're what you graph and alert on, and why ids must never be tags.

**Traces** are trees of timed spans for one request across services. They answer "where did the time go?" They're expensive per request, so they're sampled (Spring Boot's default is 10%): a metric counts every request, but the request a customer complains about may have no trace.

In an incident: a metric-based alert fires (p99 latency or error ratio), dashboards show the scope (which endpoint, which instances, since when); you open slow or failed traces from that window, and the waterfall shows the slow hop; you jump by trace id to that service's log lines for the detail. One Micrometer `Observation` produces both a timer and a span, and logs written inside it carry the trace id. That shared id is what links the three.

## 2. Why can't you put a user id in a metric tag, and how do you get per-user insight instead?

Every unique combination of metric name and tag values is a separate time series. The app keeps a meter per combination in memory, Prometheus stores and indexes each series, and hosted monitoring often bills per series. A `userId` tag grows the series count with the user base and never shrinks it: 10,000 users meant 10,000 meters in topic 03. Memory, scrape size, query speed, and cost all degrade, and a spike of new ids can take monitoring down exactly when you need it. The same goes for order ids, emails, raw URL paths (Spring tags the template `/orders/{id}` for this reason), and exception messages.

Use tags with small, fixed value sets (`plan`, `status`, `outcome`, `region`). Answer per-user questions with logs and traces, which are built for high-cardinality data. A `MeterFilter.maximumAllowableTags` caps the damage if someone adds an unbounded tag anyway, but it drops data beyond the cap: it protects the system, it doesn't fix the metric.

## 3. How do you get the p95 latency of a service running as 6 instances from Prometheus? What are the common mistakes?

Publish a histogram: `management.metrics.distribution.percentiles-histogram.http.server.requests=true` exposes cumulative `_bucket{le="..."}` counters. Then:

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[5m])))
```

The order matters: `rate` each bucket first (they're counters, and `rate` handles restarts), `sum` across instances while keeping `le`, then take one quantile. Add `uri` to the `by` for one line per endpoint.

The mistakes: averaging the six per-instance p95s, where a quiet instance weighs as much as a busy one and the result can be off by orders of magnitude (0.88 s against a real 0.0096 s in topic 04); using Micrometer's client-side `percentiles`, which are computed per instance and can't be combined; dropping `le` from the `by`; and a `rate` window shorter than about four scrape intervals. The result is an estimate interpolated inside a bucket, so put bucket boundaries at your SLO thresholds (`management.metrics.distribution.slo...`).

## 4. What's the difference between `rate`, `irate`, and `increase`, and why does a counter need them at all?

A counter is a running total since the process started. Graphed raw, it only climbs, and drops to 0 at every restart. `rate(x[5m])` is the average per-second increase over the window, extrapolated to its edges: use it for graphs and alerts. `increase(x[5m])` is the same multiplied by the window ("how many in 5 minutes"), usually not a whole number because of the extrapolation. `irate` uses only the last two samples: fine for zoomed-in graphs, too noisy to alert on.

All three treat a decrease as a reset and count the new value as growth from zero, so deploys don't show negative traffic. The same logic makes them wrong on gauges. Always `sum(rate(x))`, never `rate(sum(x))`: one instance restarting drops the sum and looks like a fleet-wide reset. They also need two samples, so a series that first appears already at 5 (Micrometer creates the `status="500"` series on the first error) never shows those 5.

## 5. How does a trace stay connected across services and threads, and where does it typically break?

The current span lives in a thread-local context. Across HTTP it travels in the W3C `traceparent` header, `00-<32-hex trace id>-<16-hex parent span id>-<flags>`, where flag `01` means sampled. The receiving service continues that trace id and makes its server span a child of the caller's client span. Spring's instrumented clients add the header and the server observation reads it.

It breaks wherever nothing copies the context: hand-made HTTP clients (`RestClient.create()`, `new RestTemplate()`) send no header, so the downstream starts a new trace; thread pools and `@Async` start with an empty context unless the executor has a `ContextPropagatingTaskDecorator`; brokers need observation switched on (`spring.kafka.template.observation-enabled`, `spring.kafka.listener.observation-enabled`) so the header rides on the message (topic 06).

Sampling is head-based and parent-based: the first service decides and the flag carries the decision, so all services keep or drop the same request. Tail sampling ("keep every error and slow trace") happens after the trace completes, in an OpenTelemetry Collector.

## 6. A customer says "the order page errored at 14:02". Walk through finding the cause, and what has to be in place beforehand.

Best case, the customer quotes a trace id from the error response (a `ProblemDetail` with `traceId`, or a response header). Otherwise start from the 5xx spike on the dashboard: an exemplar, a trace id attached to a histogram bucket in the OpenMetrics format, links to a real failing request. The trace waterfall shows which span failed and where the time went. Then search logs by that trace id across services; the log pattern or JSON fields carry `traceId` and `spanId` from the MDC (topic 07).

What must already be in place: tracing with a deliberate sampling rate; HTTP clients built from Boot's builders; decorated executors so background log lines carry the id; a catch-all exception handler that logs while the span is current, because otherwise the stack trace is logged by Tomcat with an empty trace id; histograms plus Prometheus exemplar storage (still a feature flag in Prometheus 3.5); and trace ids in error responses. Each missing piece breaks one link of the chain.

## 7. What's the difference between liveness and readiness, and what belongs in each for a Spring Boot service?

Liveness asks "is this process broken beyond repair?", and failing it restarts the container. Readiness asks "should this instance get traffic now?", and failing it only removes the pod from the Service. Spring Boot backs them with `LivenessState` (CORRECT/BROKEN) and `ReadinessState` (ACCEPTING_TRAFFIC/REFUSING_TRAFFIC), exposed at `/actuator/health/liveness` and `/readiness`. Readiness stays REFUSING_TRAFFIC until the `ApplicationRunner`s finish, and flips back to REFUSING_TRAFFIC at shutdown before graceful shutdown starts draining.

Liveness should contain only `livenessState`; a dependency there turns a database blip into a restart storm (4 restarts in a 3-minute outage in topic 08). Readiness gets `readinessState` plus what this instance needs to serve. Downstream HTTP services belong in neither: that's what timeouts and circuit breakers are for.

Two configuration traps: `include` replaces a group's members, so `readiness.include: db` silently drops `readinessState`; and configuring a group without `include` makes it contain every indicator. On the platform side, add a startup probe for slow starts, and use a failure threshold of 3 or more with a timeout that has headroom, so one GC pause doesn't restart a healthy pod.

## 8. How do you alert on an SLO without paging people for noise?

Define the SLI as good events over valid events: non-5xx requests over all requests, or requests under 300 ms over all requests. Set an SLO such as 99.9% over 30 days. The error budget is the remaining 0.1%: 43.2 minutes of total outage, or 43,200 failed requests at 1,000 requests a minute.

The burn rate is the observed error ratio divided by the budget ratio; at 1 the budget runs out exactly at the end of the window. Page on two conditions, each over a long *and* a short window: fast burn at 14.4× (2% of the monthly budget in an hour) over 1 h and 5 m, and slow burn at 6× (5% in 6 hours) over 6 h and 30 m. The long window proves enough budget has really been spent, so a 2-minute blip doesn't page; the short window makes the alert stop within minutes of the fix. Slower burns become tickets, not pages. Causes (CPU, heap, consumer lag) belong on dashboards, not in pages, and every page needs an action and a runbook.

Implement it as recording rules for the ratios plus alerting rules, and unit-test them with `promtool test rules` for healthy traffic, a blip, a fast burn, and a slow burn (topic 09). Check that the series the rules use actually exist, because a rule on a missing series never fires.
