# Logs, Metrics, and Traces

Observability is being able to answer "what is my service doing, and why?" from the outside, in production, without adding new code and redeploying. Three kinds of data, called **signals**, do most of the work: **logs**, **metrics**, and **traces**. You've met each one already (logging in module 08 topic 10, Actuator metrics in 08-12, correlation and tracing in module 13 topic 17). This topic puts them side by side, because during an incident the skill isn't knowing each tool, it's knowing which one answers the question you have right now.

---

## The problem this solves

Users report that checkout is slow. That sentence hides three different questions:

1. **Is it everyone, or a few requests? Since when? How bad?** A question about *all* requests, over time.
2. **Where does the time go?** Is it our code, the database, or the pricing service we call?
3. **What exactly happened to the request from customer 1042 at 14:02?** A question about *one* request.

No single signal answers all three well. The demo makes one call slow (the pricing service takes 200 ms) and asks each signal what it can see.

## How it works

### The demo

`CheckoutService.checkout` calls `PricingClient.priceCents`, which stands in for an HTTP call to another service. Both are wrapped in Micrometer **observations**:

```java
public long checkout(String orderId, String sku) {
    return Observation.createNotStarted("checkout", observations).observe(() -> {
        long started = System.nanoTime();
        long total = pricing.priceCents(sku) + validate(orderId);
        long tookMillis = (System.nanoTime() - started) / 1_000_000;
        log.info("checkout finished orderId={} totalCents={} tookMs={}", orderId, total, tookMillis);
        return total;
    });
}
```

An observation is Micrometer's "measure this piece of work" API. Spring Boot connects it to two handlers: one records a **timer** in the `MeterRegistry` (a metric), the other starts and ends a **span** in the tracer (a trace). One instrumentation point, two signals. The log line is the third. Spring's own HTTP server, `RestClient`, JDBC, and Kafka instrumentation all use observations the same way, so most of this comes free in a real service.

### Logs: what happened to this one request

```text
INFO [c427284bacccedee948decff8b5a742e-f30ee0ec84933707] CheckoutService : checkout finished orderId=order-42 totalCents=1999 tookMs=214
```

`ThreeSignalsTest.log_tellsYouThatThisOneRequestWasSlow_butNotWhy` finds this line and reads `tookMs=214`. A log line is an **event**: one thing that happened, with as much detail as you choose to write, including high-cardinality values like `orderId`. That's what logs are for: "what happened to order-42?"

What the line can't tell you: whether every checkout is slow or only this one (you'd have to find and count all the lines), and where the 214 ms went. The bracket at the start is the trace id, which Spring Boot adds to the log pattern when tracing is on. It's the link from this line to the trace (topic 07).

### Metrics: how many, how often, how slow, across everything

`metrics_tellYouHowManyAndHowSlow_acrossAllRequests_butNotWhichOne` runs four fast checkouts and one slow one, then reads the timers:

```java
Timer checkoutTimer = meters.get("checkout").timer();
checkoutTimer.count();                       // 5
checkoutTimer.max(TimeUnit.MILLISECONDS);    // >= 200: something was slow
checkoutTimer.mean(TimeUnit.MILLISECONDS);   // much lower: most requests were fine

meters.get("pricing.call").tag("downstream", "pricing-service").timer()
        .max(TimeUnit.MILLISECONDS);         // >= 200 too: the slow part is the pricing call
```

A metric is a **number aggregated over time**: a count, a sum, a maximum, a distribution. It costs the same whether you handle 5 requests or 5 million, which is why metrics are what you graph and alert on (topics 04, 05, 09). Comparing the `checkout` timer with the `pricing.call` timer already points at pricing-service.

What metrics give up is the individual request. There's no `orderId` tag, on purpose: every distinct tag value creates a new time series, and a tag per order would create millions of them (topic 03). So a metric says "1 in 5 checkouts took over 200 ms", never "order-42 did".

### Traces: where the time went, inside one request

`trace_showsWhereTheTimeWent_insideThisOneRequest` reads the spans the tracer exported:

```text
checkout      |=================================|  216 ms   traceId c427...
  pricing.call |================================|  214 ms   parent = checkout
```

```java
assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
assertThat(childMs).isGreaterThanOrEqualTo(parentMs * 9 / 10);   // 90%+ of the request is the pricing call
```

A trace is the **tree of spans** for one request: each span is a timed piece of work with a parent. In a real system the child spans come from other services, linked by the `traceparent` header (module 13 topic 17), and a tracing UI draws this waterfall across all of them. It answers "where did the time go?" in one look.

The demo exports spans with `InMemorySpanExporter`, which keeps them in a list instead of sending them to a backend. In production the exporter sends them to Jaeger, Zipkin, Tempo, or an OpenTelemetry collector (topic 06).

### Sampling: why you can't trace everything

Every span costs network, storage, and money, and a busy service creates millions of them. So tracers keep a **sample**. Spring Boot's default is `management.tracing.sampling.probability=0.1`: 1 trace in 10.

`SamplingTest.metricsCountEveryRequest_tracesKeepOnlyASample` runs 200 checkouts with that default:

```text
traced checkouts: 18 of 200
```

The `checkout` timer still counts all 200. Metrics are complete; traces are a sample. That's fine for "where does the time usually go?", but the one request a customer complains about may not have been traced. The demo's `application.yml` sets the probability to 1.0 so every test request is traced; don't copy that to a busy production service without checking the cost. (Tail-based sampling, done in an OpenTelemetry collector, keeps all slow and failed traces and samples the rest. That's collector configuration, outside this module.)

---

## The three signals side by side

| | Logs | Metrics | Traces |
|---|---|---|---|
| What it is | an event with details | a number aggregated over time | a tree of timed spans for one request |
| Answers | what happened to *this* request? | how many, how often, how slow, for *all* requests? | where did the time go in *this* request, across services? |
| Cost grows with | traffic × lines per request | number of distinct time series (names × tag values) | traffic × spans per request (so: sampled) |
| High-cardinality data (ids) | yes | no | yes, as span attributes |
| Typical tool | Loki, Elasticsearch | Prometheus + Grafana (topics 04–05) | Jaeger, Zipkin, Tempo (topic 06) |
| Use it to | investigate details | detect and alert | locate the slow or failing hop |

### Which one during an incident

The usual order is **metrics → traces → logs**:

1. **A metric fires an alert** (topic 09): checkout p99 latency is above 1 s. Dashboards show since when, and whether it's every instance or one.
2. **Traces locate it**: open a few slow traces from that period. The waterfall shows the pricing call taking 900 ms.
3. **Logs explain it**: jump from the trace id to the logs of pricing-service for that request (topic 07) and read the error or the slow query.

Starting from logs is the expensive way round: searching millions of lines for a pattern you haven't found yet.

---

## When to use it / when not to

- **Metrics for anything you alert on or graph.** Request rate, errors, latency, queue depth, pool usage. Cheap, complete, but no per-request detail.
- **Logs for decisions and failures.** A log line per important business event and every error, with ids. Not a log line per loop iteration: logs cost money per line.
- **Traces as soon as a request crosses more than one service** (or a database and a cache). With a single service and good metrics, traces add less.
- **Don't use logs as metrics.** Counting log lines to graph "orders per minute" is slow and expensive, and breaks when someone rewords the message. Use a counter.
- **Don't use metrics as logs.** Putting `orderId` or `userId` in a metric tag explodes the number of time series (topic 03).

## Common pitfalls

- **Expecting to find the one complained-about request in the traces.** With 10% sampling, 9 in 10 requests have no trace. Find it in the logs, or sample errors and slow requests on purpose.
- **A metric with an id as a tag.** Every value creates a time series; memory and the monitoring bill grow with traffic. Ids belong in logs and span attributes.
- **Logging after the observation ends.** The trace id is only in the logging context while the span is open. Log inside the observed work (as the demo does) or the line has no trace id.
- **Tracing in tests with Spring Boot.** Boot sets `management.tracing.enabled=false` in tests unless you add `@AutoConfigureObservability`. In Boot 3.5 that only switches off Boot's own exporters (Zipkin, OTLP); a `SpanExporter` bean you define, like the demo's in-memory one, still receives spans.

---

## Code examples

`CODE_EXAMPLES/01-logs-metrics-traces/three-signals-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/main/java/com/example/signals/PricingClient.java`: the slow downstream call, wrapped in an observation.
2. `src/main/java/com/example/signals/CheckoutService.java`: the request, its observation, and its log line.
3. `src/main/resources/application.yml`: sampling probability.
4. `src/test/java/com/example/signals/InMemorySpans.java`: the in-memory span exporter.
5. `src/test/java/com/example/signals/ThreeSignalsTest.java`: the same slow call seen as a log line, as timers, and as a span tree.
6. `src/test/java/com/example/signals/SamplingTest.java`: metrics count 200 requests, traces keep about 20.

---

## Try this yourself

1. Add a third step inside `checkout`, a `stock.check` observation that takes 50 ms. Extend the trace test to assert three spans, and check whether the `checkout` timer alone could tell you about the new step.
2. Make `PricingClient` throw for SKU `bad`. What does each signal show? Look at the `error` tag on the timer, the span's status, and the log output.

## Self-check

1. A customer says their order at 14:02 was slow. Which signal do you open first, and why might the trace you want not exist?
2. Why can a timer tell you "1 in 5 checkouts was slow" but never "order-42 was slow"?
3. Name the usual order of signals during an incident, and what each step tells you.
