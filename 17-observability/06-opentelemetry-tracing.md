# OpenTelemetry Tracing

A **trace** is the story of one request: every piece of work it caused, in every service, with how long each took and which one failed. Module 13 topic 17 built a correlation id by hand and named Micrometer Tracing as the real tool. This topic uses that tool with the **OpenTelemetry** implementation: what a span contains, how the trace crosses an HTTP call, where it silently breaks (a hand-made HTTP client, a thread pool), and why most traces are never exported.

The demo needs no Docker. Finished spans go to an in-memory exporter that the tests read.

---

## The problem this solves

Checkout takes 2.4 seconds. The checkout service calls inventory, which calls the database; checkout also calls pricing, which calls a currency service. Logs show each service did *something*. Metrics show checkout is slow. Neither tells you that 2.1 of those seconds were inventory waiting for one query, and that pricing was fine.

A trace answers that directly. It's a tree of timed steps for one request, drawn as a waterfall in a tracing backend (Jaeger, Grafana Tempo, Zipkin):

```text
trace 4bf92f35...                         0 ms                         2400 ms
http get /checkout          (checkout)    |=============================|
  http get                  (client)       |=========================|
    http get /stock/{sku}   (inventory)     |========================|
      check-stock                            |=======================|    <- here
  http get                  (client)                                  |=|
    http get /price/{sku}   (pricing)                                 |=|
```

## How it works

### Traces and spans

A **span** is one timed piece of work: handling a request, making an outgoing call, running a query, a block of your own code. Each span has:

| Field | Example | Meaning |
|---|---|---|
| trace id | `4bf92f3577b34da6a3ce929d0e0e4736` | 32 hex characters, shared by every span of one request |
| span id | `00f067aa0ba902b7` | 16 hex characters, this span only |
| parent span id | the span id of the caller | builds the tree; a root span has none |
| name | `http get /stock/{sku}` | what the work was |
| kind | `SERVER`, `CLIENT`, `INTERNAL`, `PRODUCER`, `CONSUMER` | which side of a call it is |
| start, end | timestamps | the duration |
| attributes | `uri=/stock/{sku}`, `status=200` | key-value details you can search on |
| events | `cache-miss` at 12 ms | timestamped notes inside the span |
| status | `UNSET`, `OK`, `ERROR` | whether it failed |

### Setting it up in Spring Boot

Three dependencies, one property:

```xml
<dependency>  <!-- tracing auto-configuration lives in Actuator -->
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>  <!-- the Micrometer Tracing API, implemented by OpenTelemetry -->
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>  <!-- sends spans over OTLP to a collector, Jaeger, or Tempo -->
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0                                     # default 0.1, see "Sampling" below
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces       # where spans are sent
```

Your code talks to Micrometer's API (`Tracer`, `Observation`), and OpenTelemetry does the work underneath. That's the same arrangement as SLF4J over Logback: swap the bridge (`-bridge-brave`) and your code doesn't change. **OTLP** (OpenTelemetry Protocol) is the standard wire format; most teams send spans to an **OpenTelemetry Collector**, which batches, filters, and forwards them to whatever backend stores them.

The demo replaces the OTLP exporter with an in-memory one, which is just another `SpanExporter` bean:

```java
@Bean
InMemorySpanExporter spanExporter() {
    return InMemorySpanExporter.create();   // from io.opentelemetry:opentelemetry-sdk-testing
}
```

Boot sends finished spans to every `SpanExporter` bean through a `BatchSpanProcessor`, which exports in batches every 5 seconds. The tests call `SdkTracerProvider.forceFlush()` before reading spans instead of waiting.

**Tests have tracing turned off.** Spring Boot's test support disables tracing in `@SpringBootTest` unless the class has `@AutoConfigureObservability`. Without it, every span assertion in the demo fails.

### What you get without writing any tracing code

One request to `/stock/A1` produces a server span with the HTTP details as attributes (`SpanTest.spring_tagsTheServerSpan_withMethodRouteAndStatus`):

```text
http get /stock/{sku}  kind=SERVER  parent=none
  method=GET  uri=/stock/{sku}  http.url=/stock/A1  status=200  outcome=SUCCESS  exception=none
```

The span name uses the route template (`/stock/{sku}`), not the real URL, so all stock lookups group together. The real URL is in `http.url`.

### Your own spans: the Observation API

Wrap a block of work in an `Observation` and it becomes a child span of whatever span is current. It also becomes a timer metric (topic 03), which is why Spring calls it an *observation* rather than a span:

```java
public int check(String sku) {
    return Observation.createNotStarted("stock.check", registry)
            .contextualName("check-stock")                  // the span's name
            .lowCardinalityKeyValue("warehouse", "eu")      // span attribute AND metric tag: few values only
            .highCardinalityKeyValue("sku", sku)            // span attribute only: any number of values
            .observe(() -> lookUp(sku));
}

private int lookUp(String sku) {
    tracer.currentSpan().event("cache-miss");               // a timestamped note inside the span
    ...
}
```

`SpanTest.oneRequest_isOneTrace_aServerSpanWithAChildSpanInside` checks the tree: `check-stock` has the server span's trace id, and its parent span id is the server span's span id. `aCustomSpan_carriesYourAttributesAndEvents` finds `warehouse=eu`, `sku=A1`, and the `cache-miss` event.

The low/high split matters. A low-cardinality key value becomes a metric tag too, and a tag with thousands of values creates thousands of time series (topic 03). The sku goes on the span only.

For a whole method, the annotation does the same:

```java
@Observed(name = "pricing.lookup", contextualName = "look-up-price")
public long price(String sku) { ... }
```

It needs `spring-boot-starter-aop` and `management.observations.annotations.enabled=true`. Like `@Transactional`, it works through a proxy, so a call from the same class isn't observed (module 02 topic 16).

### Errors

When the observed code throws, the span is marked as failed (`anException_marksTheSpansAsErrors_andRecordsTheException`):

```text
check-stock            status=ERROR "no such sku: unknown-1"  events=[cache-miss, exception]
http get /stock/{sku}  status=ERROR "no such sku: unknown-1"  status=500  exception=IllegalArgumentException
```

The `exception` event holds the type, message, and stack trace. In a tracing backend you can search for `status=ERROR` traces and land on the span where it started.

---

## Propagation: how a trace crosses a service boundary

### The `traceparent` header

When checkout calls inventory, the trace continues only if checkout tells inventory which trace it's in. The standard way is the W3C Trace Context header:

```text
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             |  |                                |                |
             |  trace id (32 hex)                parent span id   flags: 01 = sampled
             version                             (the caller's client span)
```

Inventory's server span gets that trace id and uses that span id as its parent. `PropagationTest.anIncomingTraceparent_isContinued` sends a hand-written header and gets the same trace id back.

### Fixed: an HTTP client built from Boot's builder

```java
@Bean
RestClient tracedClient(RestClient.Builder builder) {   // Boot's builder has observation wired in
    return builder.build();
}
```

`restClientFromBootsBuilder_sendsTraceparent_soTheDownstreamContinuesTheTrace`: the downstream receives `00-<same trace id>-<span id>-01`, and the whole request is one trace of four spans: checkout (server), the outgoing call (client), stock (server), check-stock.

### Broken: an HTTP client made by hand

```java
@Bean
RestClient untracedClient() {
    return RestClient.create();   // no observation, no traceparent
}
```

`restClientCreate_sendsNoTraceparent_soTheDownstreamStartsANewTrace`: the downstream receives no `traceparent`, starts its own trace, and the outgoing call has no client span at all. In the backend you see two unrelated traces, and the slow downstream call is invisible from checkout's side. No error, no warning.

The same happens with `new RestTemplate()` and `WebClient.create()`. Always build clients from the injected `RestClient.Builder`, `RestTemplateBuilder`, or `WebClient.Builder`. (The same rule applies to `TestRestTemplate` in tests: it's built from Boot's builder, so it's traced too.)

### Threads: the trace lives in a thread-local

The current span is stored per thread. Work handed to another thread starts with no span unless something copies it across.

```java
@Async
public CompletableFuture<String> build() {
    Span span = tracer.currentSpan();   // null on the worker thread unless context is propagated
    ...
}
```

`AsyncNoPropagationTest.withoutTheDecorator_theAsyncThreadHasNoTrace`: Boot's `@Async` executor doesn't propagate it on its own, so the worker sees no span. Anything it logs has no trace id, and any HTTP call it makes starts a new trace.

The fix is one bean. Boot applies any `TaskDecorator` bean to its `@Async` executor, and `ContextPropagatingTaskDecorator` (Spring Framework 6.1+) copies the trace context onto the worker thread:

```java
@Bean
ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
    return new ContextPropagatingTaskDecorator();
}
```

`AsyncPropagationTest.withContextPropagatingTaskDecorator_theAsyncThreadIsInTheSameTrace`: same trace id on both threads. An executor you create yourself (`Executors.newFixedThreadPool`) gets nothing from Boot: wrap it with `ContextExecutorService.wrap(...)` from the `context-propagation` library, or use a `ThreadPoolTaskExecutor` with `setTaskDecorator(new ContextPropagatingTaskDecorator())`.

### Messages

Across Kafka the trace id travels in a record header, the same way as over HTTP. Spring Kafka adds and reads it only when observation is switched on: `spring.kafka.template.observation-enabled=true` for `KafkaTemplate` and `spring.kafka.listener.observation-enabled=true` for listeners. Spring AMQP has the same two settings under `spring.rabbitmq`. Without them the trace stops at the broker (module 16).

---

## Sampling: why most traces are missing

Exporting every span of every request costs network, storage, and money. So Spring Boot samples: **the default `management.tracing.sampling.probability` is 0.1**, so 1 trace in 10 is kept. Teams add tracing, open Jaeger, find nothing for the request they're debugging, and decide tracing is broken. Set the probability on purpose: 1.0 in development and test, and in production whatever your volume and budget allow.

Two things the demo shows about a trace that isn't sampled (`SamplingTest`, probability 0.0):

- **It still has a trace id**, and the id is still propagated and still goes into log lines (topic 07). Only *export* is skipped.
- **Nothing is exported.** 20 requests, zero spans.

The decision is made once, by the first service, and carried in the `traceparent` flags. Boot's sampler is *parent-based*: if the caller says "sampled", you sample; if it says "not sampled" (`-00`), you don't, whatever your own probability. `anUpstreamNotSampledDecision_isRespected_evenAtProbabilityOne` sends flags `00` to an app set to 1.0 and gets no spans. That keeps traces complete: every service keeps or drops the same requests.

This is **head sampling**: decided at the start, before anyone knows whether the request will be slow or fail. So 90% of your errors are dropped along with 90% of everything else. **Tail sampling** decides after the whole trace has finished ("keep every trace with an error or over 1 second, plus 5% of the rest"). It needs every span collected in one place first, so it runs in the OpenTelemetry Collector, not in your application.

---

## When to use it / when not to

- **Use it for any request that crosses a service or a thread pool.** It's a dependency and a property, and it's the only signal that shows where time went across services.
- **A single service with no outgoing calls** gets less from it. Metrics and logs with a trace id (topic 07) may be enough.
- **Sample deliberately.** 1.0 everywhere is expensive at scale; 0.1 without knowing it loses the traces you need. Consider tail sampling in a collector if you mostly care about errors and slow requests.
- **Don't put secrets or personal data in attributes.** Spans are shipped to another system and kept for days.

## Common pitfalls

- **A hand-made HTTP client** (`RestClient.create()`, `new RestTemplate()`): the trace silently splits in two. Build clients from Boot's builders.
- **`@Async` or your own executor without context propagation**: the worker thread has no trace. Add a `ContextPropagatingTaskDecorator` bean, or wrap your executor.
- **Not knowing the 10% default**: "tracing doesn't work" when it's sampling. Set `management.tracing.sampling.probability`.
- **High-cardinality values as low-cardinality key values**: an id as a low-cardinality key value becomes a metric tag and creates a time series per id. Use `highCardinalityKeyValue`.
- **Missing `@AutoConfigureObservability` in a test**: spans never appear and the test fails.

---

## Code examples

`CODE_EXAMPLES/06-opentelemetry-tracing/tracing-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/main/resources/application.yml`: sampling at 1.0, `@Observed` switched on.
2. `src/main/java/com/example/tracing/TracingApplication.java`: the in-memory exporter, a traced and an untraced `RestClient`, the task decorator.
3. `src/main/java/com/example/tracing/StockService.java`: a custom span with the Observation API, an event, an error.
4. `src/main/java/com/example/tracing/PricingService.java`: the same with `@Observed`.
5. `src/main/java/com/example/tracing/ShopController.java`: the endpoints the tests call.
6. `src/test/java/com/example/tracing/Spans.java` and `TracingTestBase.java`: flushing and reading spans; `@AutoConfigureObservability`.
7. `src/test/java/com/example/tracing/SpanTest.java`: the span tree, attributes, events, errors.
8. `src/test/java/com/example/tracing/PropagationTest.java`: `traceparent`, the broken client, an incoming trace, an upstream sampling decision.
9. `src/main/java/com/example/tracing/ReportService.java`, `AsyncNoPropagationTest.java`, `AsyncPropagationTest.java`: the trace across `@Async`.
10. `src/test/java/com/example/tracing/SamplingTest.java`: the 0.1 default; unsampled requests export nothing.

---

## Try this yourself

1. In `StockService`, change `highCardinalityKeyValue("sku", sku)` to `lowCardinalityKeyValue`. Expose the metrics endpoint (`management.endpoints.web.exposure.include=metrics`), start the app, call `/stock/A1`, `/stock/A2`, `/stock/A3`, then open `/actuator/metrics/stock.check`. How many values does the `sku` tag have, and what would happen with a million products?
2. In `ReportService`, make the async method call `/stock/A1` through the traced `RestClient`. Run `AsyncNoPropagationTest`: how many traces does one `/report` request produce now?
3. Set the sampling probability to 0.5 in a new test, send 100 requests, and count the distinct trace ids in the exported spans. Why isn't it exactly 50?

## Self-check

1. A request goes checkout → inventory → database. Name the spans you'd expect, their kinds, and which span is each one's parent.
2. Write out a `traceparent` header and explain each part. Which part tells the next service whether to export its spans?
3. Checkout's trace in Jaeger ends at its outgoing call, and inventory shows a separate trace for the same request. Give two different causes.
