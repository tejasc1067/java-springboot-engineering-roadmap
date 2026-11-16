# Micrometer Metrics

Module 08 topic 12 showed that Spring Boot exposes metrics and that a counter takes three lines. This topic is about choosing and shaping metrics well: which meter type fits which question, how names and tags turn into time series, and the mistake that makes a metrics system fall over: putting an id in a tag. **Micrometer** is the metrics API Spring Boot uses. You record against it, and a registry (Prometheus, Datadog, and so on) decides how the numbers are exported, the way SLF4J sits in front of Logback.

---

## The problem this solves

"How many orders per minute? How slow is checkout for most users, and for the slowest 1%? How many emails are waiting to be sent?" These are questions about **all** requests over time, and they need to be answered cheaply, continuously, and fast enough to alert on (topic 01). A metric answers each with a few numbers, whatever the traffic. Asked the wrong way (an average instead of a percentile, a gauge that silently stops, a tag per user), the same metric is misleading or ruinously expensive.

## How it works

### Names and tags make a time series

A meter has a **name** and **tags** (key-value pairs, also called labels or dimensions):

```java
Counter.builder("orders.placed").tag("plan", "pro").register(registry).increment();
```

Each unique combination of name and tag values is one **time series**: one line on a graph, stored separately by the monitoring system. `orders.placed{plan=pro}` and `orders.placed{plan=free}` are two series, and you can sum them or compare them.

Naming conventions:

- **Lowercase, dot-separated, in Java:** `orders.placed`, `http.server.requests`. Each registry converts to its own style; Prometheus gets `orders_placed_total`.
- **Name the thing, not the unit, and record in base units.** Micrometer exports timers in seconds and summaries in the base unit you give, so the unit is appended for you.
- **Tag values from a small, fixed set:** `plan`, `status`, `outcome`, `method`. Never ids (below).

`registeringTheSameNameAndTagsAgain_returnsTheSameCounter`: calling `register` twice with the same name and tags returns the existing meter, so it's safe to build meters inline.

### Counter: how many

```java
Counter placed = Counter.builder("orders.placed").register(registry);
placed.increment();
```

A counter only goes up. There's no `decrement()`. "Orders per second" isn't stored anywhere; Prometheus computes it from how fast the total grows (topic 04). If the value can go down, you want a gauge.

### Gauge: what is it right now

```java
ConcurrentLinkedQueue<String> emailQueue = new ConcurrentLinkedQueue<>();
Gauge.builder("email.queue.size", emailQueue, ConcurrentLinkedQueue::size).register(registry);
```

A gauge doesn't store a value. It reads the current value (`emailQueue.size()`) each time it's sampled, which is why it can go up and down (`aGaugeReadsTheCurrentValueWhenSampled`). Use it for queue depth, pool usage, cache size: things you *measure*, not *count*.

### Broken: a gauge on an object nobody keeps

```java
registry.gauge("pending.orders", new ArrayList<>(List.of("o-1", "o-2", "o-3")), List::size);
```

Micrometer holds the gauged object through a **weak reference**, so it doesn't keep your objects alive by accident. Here nothing else refers to the list, so the garbage collector removes it, and the gauge reports `NaN` from then on. `aGaugeOnAnObjectNobodyKeeps_turnsIntoNaN` shows it after a few `System.gc()` calls. In production it happens at some random point after startup, and the dashboard shows a gap with no error anywhere.

**Fix:** gauge an object that something else keeps (a field of a Spring bean, the real queue), or pass `.strongReference(true)` on the builder (`aStrongReferenceGauge_keepsReporting`).

### Timer: how long, and how many

```java
Timer.Sample sample = Timer.start(clock);
// ... the work ...
sample.stop(checkout);
```

A timer records a count, a total time, and a maximum. `TimerTest.aTimerRecordsCountTotalAndMax` uses Micrometer's `MockClock`, so the durations are exact: requests of 100, 120, and 900 ms give count 3, total 1,120 ms, max 900 ms, and a **mean of 373 ms that matches no request at all**. Averages hide slow requests. That's why latency is reported as percentiles: "95% of requests are faster than X".

A **distribution summary** is the same for things that aren't time, such as payload sizes: `DistributionSummary.builder("http.request.body.size").baseUnit("bytes")`.

### Percentiles: two ways, very different

`PrometheusFormatTest` shows what each option exports:

```java
Timer.builder("checkout").publishPercentiles(0.95)          // the app computes p95 itself
Timer.builder("checkout").publishPercentileHistogram()       // the app exports buckets; Prometheus computes
```

- **`publishPercentiles(0.95)`** exports a ready-made number, `checkout_seconds{quantile="0.95"}`, computed inside one instance. You **can't combine** these across instances: the p95 of three instances isn't the average of their three p95s. Fine for a single instance, wrong for a service with several.
- **`publishPercentileHistogram()`** exports bucket counters (`checkout_seconds_bucket{le="0.1"}`, ...) that Prometheus can add up across instances and turn into any percentile with `histogram_quantile` (topic 04). The cost: `aPercentileHistogram_exportsBuckets...` counts **69 bucket series for one timer**, multiplied by every tag combination. Enable it on the few timers you alert on.

Spring Boot can switch histograms on without code: `management.metrics.distribution.percentiles-histogram.http.server.requests=true`.

### What a scrape looks like

`namesUseDotsInJava_andBecomeSnakeCaseWithUnitsInPrometheus`:

```text
orders_placed_total 1.0
checkout_seconds_count 1
checkout_seconds_sum 0.25          <- recorded as 250 ms, exported in seconds
checkout_seconds_max 0.25
http_request_body_size_bytes_sum 512.0
```

Dashboards and alerts use these exported names, so a rename in Java is a breaking change for them.

### Broken: an id as a tag

```java
public void orderPlacedBroken(String userId) {
    Counter.builder("orders.placed").tag("userId", userId).register(registry).increment();
}
```

`CardinalityTest.aUserIdTag_createsOneTimeSeriesPerUser`: 10,000 users give **10,000 meters** in the app and 10,000 series in each scrape. Every meter lives in the app's memory forever, Prometheus stores each series separately, and hosted monitoring is usually billed per series. The next 10,000 users add 10,000 more. The same happens with order ids, emails, session ids, raw URLs (`/orders/123` instead of the template `/orders/{id}`, which Spring's own HTTP metrics use), and exception messages. Each of these is **high cardinality**: a tag with an unbounded number of values.

### Fixed: tag by a bounded value

```java
public void orderPlaced(String plan) {
    Counter.builder("orders.placed").tag("plan", plan).register(registry).increment();
}
```

`aBoundedTag_keepsTheSeriesCountFixed_howeverManyUsersThereAre`: 10,000 users, 2 meters. "Which user?" is a question for logs and traces (topic 01).

As a safety net, a `MeterFilter` can cap a tag:

```java
registry.config().meterFilter(
        MeterFilter.maximumAllowableTags("orders.placed", "userId", 100, MeterFilter.deny()));
```

`aMeterFilter_capsTheDamage_whenSomeoneAddsAnUnboundedTag`: after 100 distinct values, new ones are denied, and the meter count stays at 100. Note what that costs: orders from the other 9,900 users weren't counted at all. The filter protects the system; it doesn't make the metric correct. Register filters before meters are created (in Spring Boot, declare a `MeterFilter` bean), because they don't apply to meters that already exist.

### `@Timed`, `@Counted`, and observations

```java
@Timed("invoice.render")
public String render(String orderId) { ... }
```

The annotation is only metadata. Something has to read it: an aspect. Spring Boot creates one only when `management.observations.annotations.enabled=true` **and** AOP is on the classpath (`spring-boot-starter-aop`). `TimedDisabledTest`: with the defaults, no `invoice.render` timer exists, and nothing warns you. `TimedEnabledTest`: with the property set, the timer counts 2 calls. Like every proxy-based annotation, it also doesn't work on calls from inside the same class (module 02 topic 16).

For your own code, the **Observation API** (topic 01) is often better than a plain timer: one `Observation` gives you a timer *and* a span, and Spring's own HTTP, database, and messaging instrumentation is built on it.

---

## When to use it / when not to

| Meter | Question it answers | Example |
|---|---|---|
| Counter | how many (rate per second, computed later) | orders placed, payments failed |
| Gauge | what is it now (can go down) | queue depth, active connections |
| Timer | how long and how many | request latency, a downstream call |
| Distribution summary | how big and how many | payload size, items per order |

- **Use metrics for what you'll graph or alert on.** A metric nobody looks at costs memory and money.
- **Don't record what Boot already records.** HTTP server and client requests, JVM memory and GC, connection pools, caches, and executors come built in. Look at `/actuator/metrics` before adding your own.
- **Histograms only where needed.** Each one adds dozens of series per tag combination.

## Common pitfalls

- **An id as a tag** (user, order, raw URL, exception message). Series grow with traffic. Use a bounded tag; put ids in logs and spans.
- **A gauge on an object nobody else references.** It turns into `NaN` after a garbage collection. Gauge a field of a bean, or use `strongReference(true)`.
- **Alerting on the average.** It hides the slow requests. Use percentiles from a histogram.
- **`publishPercentiles` on a service with several instances.** Per-instance percentiles can't be combined. Use `publishPercentileHistogram`.
- **`@Timed` without `management.observations.annotations.enabled=true` and AOP.** It records nothing, silently.

---

## Code examples

`CODE_EXAMPLES/03-micrometer-metrics/micrometer-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/test/java/com/example/metrics/CounterAndGaugeTest.java`: counters, gauges, and the gauge that turns into `NaN`.
2. `src/test/java/com/example/metrics/TimerTest.java`: timers with a `MockClock`, the misleading mean, a distribution summary.
3. `src/test/java/com/example/metrics/PrometheusFormatTest.java`: exported names and units, client-side percentiles vs histogram buckets.
4. `src/main/java/com/example/metrics/OrderMetrics.java`: an unbounded tag and a bounded one.
5. `src/test/java/com/example/metrics/CardinalityTest.java`: 10,000 series, 2 series, and a `MeterFilter` cap.
6. `src/main/java/com/example/metrics/InvoiceRenderer.java`, `src/test/java/com/example/metrics/TimedDisabledTest.java`, and `TimedEnabledTest.java`: `@Timed` silently ignored, then working.

---

## Try this yourself

1. In `PrometheusFormatTest`, add `.serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500))` to a timer without a histogram. Which bucket lines appear, and how many series is that compared with 69?
2. Add a `MeterFilter` bean to the demo application that renames `invoice.render` to `billing.invoice.render`, and update `TimedEnabledTest`. Why must filters be registered before meters are created?
3. Add a tag `outcome` (`success` or `failure`) to `orders.placed`. How many series can the counter have now with the `plan` tag? Write the formula.

## Self-check

1. Why does a `userId` tag on a counter become a production problem, and where should the user id go instead?
2. A gauge on a dashboard shows data for an hour after startup, then `NaN` forever. What happened, and what are two fixes?
3. What's the difference between `publishPercentiles(0.95)` and `publishPercentileHistogram()`, and which one works for a service with five instances?
