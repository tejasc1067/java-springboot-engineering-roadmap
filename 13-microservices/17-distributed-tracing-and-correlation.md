# Distributed Tracing and Correlation

In a monolith, one request produces one sequence of log lines in one file — when something breaks, you read the stack trace and the surrounding logs. In a distributed system, one user request fans out across order-service, inventory-service, payment-service, each on its own host with its own log stream, all interleaving the logs of thousands of *other* concurrent requests. A failure surfaces three hops away from its cause (topic 02). Without a way to tie those scattered log lines back to the one request, debugging is hopeless. **Correlation** gives every request a shared id that appears in every service's logs; **distributed tracing** builds on that to show the request's full path and timing across services.

The demo builds the correlation mechanism by hand so it's observable, and the markdown covers Micrometer Tracing, the tool that automates it in production.

---

## The problem this solves

A user's "place order" fails with a `500`. The failure was actually in payment-service, called by order-service, called through the gateway. To debug, you need to find *this request's* log lines in payment-service — but payment-service is logging thousands of other requests at the same time, and nothing in its logs says "this one belongs to the order that failed." You're grepping timestamps and guessing.

Give the request one **correlation id** the moment it enters the system, carry it through every hop, and include it in every log line, and the problem evaporates: `grep <id>` across all services' logs reassembles the entire request in order, wherever it went. That single id is the thread that stitches a distributed request back together.

## How it works: assign, carry, log, propagate

Three moving parts, all in the demo:

**1. Assign an id per request (and reuse an upstream one).** A servlet filter runs on every request: if the caller already sent a correlation id (from the gateway or an upstream service), reuse it; otherwise generate one.

```java
String correlationId = request.getHeader(HEADER);
if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
MDC.put(MDC_KEY, correlationId);           // (2) into the logging context
response.setHeader(HEADER, correlationId); // echo it back
try { filterChain.doFilter(request, response); } finally { MDC.remove(MDC_KEY); }
```

**2. Put it in the logging context (MDC).** SLF4J's **MDC** (Mapped Diagnostic Context) is a thread-local map the logging framework can read. With the id in the MDC and a logging pattern that references it, *every* log line during the request automatically carries the id:

```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%X{correlationId}] %-5level %logger - %msg%n"
#                               └─ pulls "correlationId" from the MDC into every line
```

**3. Propagate it on outbound calls.** When order-service calls inventory-service, an interceptor copies the current correlation id onto the outbound request, so inventory-service's filter *reuses* it rather than generating a new one:

```java
String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
if (correlationId != null) request.getHeaders().set(HEADER, correlationId);
```

The demo's end-to-end test proves both cases: when no id is supplied, order-service generates one and inventory-service receives that *same* id; when the caller supplies `X-Correlation-Id: trace-abc-123` (as a gateway would), it flows through unchanged. One request, one id, across the hop — exactly what you need to grep the logs.

**The MDC cleanup matters:** the `finally { MDC.remove(...) }` is not optional. Threads are pooled and reused; if you don't clear the id, the *next* request served by that thread inherits the previous request's id and your logs lie. This is the most common correlation bug.

## From correlation to tracing: what Micrometer adds

A correlation id gets you grepped logs. **Distributed tracing** is the richer version, and in Spring you get it from **Micrometer Tracing** (the successor to Spring Cloud Sleuth). Adding it (a `micrometer-tracing-bridge-brave` or `-otel` dependency) does automatically what the demo does by hand, plus more:

- **Trace id + span id.** A whole request is a **trace** (one id); each unit of work within it (a service handling it, a downstream call) is a **span** (its own id, linked to a parent). So you don't just know *which* request — you know its *structure*: order-service's span, the inventory call span nested inside it, etc. Micrometer puts `[traceId, spanId]` in the MDC for you — no filter to write.
- **Automatic propagation** via the **W3C `traceparent`** header (or B3), instrumented into `RestClient`/`RestTemplate`/messaging automatically — no interceptor to write.
- **Timing.** Each span records how long it took, so you can see *where* the latency is across the chain (was it the payment call? the DB?).
- **Export to a backend.** Spans are sent (sampled) to a tracing system — **Zipkin**, **Jaeger**, or an OpenTelemetry collector — which shows a visual waterfall of the request across all services. That backend is infrastructure (and, like metrics dashboards, belongs to module 19); the *propagation* is the part that's a microservices concern and lives here.
- **Sampling.** Tracing every request is expensive at scale, so tracers sample (e.g. 10%). You configure the rate; correlation ids for logging are usually kept on every request even when trace *export* is sampled.

The demo is deliberately the by-hand version so you can see the mechanism; reach for Micrometer Tracing in real services rather than hand-rolling a filter — it's a dependency plus a little config, and it does spans and export too.

## When to use it / when not to

- **Always, in any multi-service system.** A correlation id on every request (and in every log line) is table stakes — the cost is near zero and the debugging payoff is enormous. Add it from day one.
- **Full tracing (spans + export)** once you have enough services that "where's the latency / where did it fail across the chain?" is a real question. Turn on Micrometer Tracing and point it at Zipkin/Jaeger; sample to control cost.
- **Not just HTTP.** Propagate the id across *async* hops too (topic 07) — put it in the message headers so an event consumer logs the same id as the request that published the event. A correlation id that stops at the message broker leaves a gap.
- **Don't hand-roll it in production** when Micrometer Tracing exists. The demo hand-rolls to teach; real services use the library.

## Common pitfalls

- **Not clearing the MDC.** Pooled threads reuse the id across requests, so logs attribute lines to the wrong request. Always clear in `finally` (the demo does).
- **Generating a new id at each hop instead of propagating.** If every service makes its own id, you can't correlate across them — the whole point is lost. Reuse the incoming id; only generate when there isn't one.
- **Forgetting async propagation.** The id rides HTTP headers automatically but not message payloads unless you put it there. An event-driven flow (topic 07) needs the id in the message headers or the trace breaks at the broker.
- **Logging without the id in the pattern.** The id is in the MDC but the log pattern doesn't reference `%X{...}`, so it never appears — you did the work and get none of the benefit.
- **Sampling confusion.** Sampling reduces exported *traces* (for cost), but you usually still want the correlation id on every log line. Don't let trace sampling drop your log correlation.
- **Treating tracing as a replacement for good logs/metrics.** Tracing shows the path and timing; you still need meaningful log messages and metrics (module 19). They complement each other.

---

## Code examples

Runnable demo — `17-distributed-tracing-and-correlation/correlation-demo/`. Read then `mvn test`:

1. `correlation-demo/src/main/java/com/example/tracing/CorrelationIdFilter.java` — assign/reuse the id, put it in the MDC, clear it afterwards.
2. `correlation-demo/src/main/java/com/example/tracing/CorrelationIdPropagationInterceptor.java` — forward the id on outbound calls.
3. `correlation-demo/src/main/resources/application.yml` — the log pattern that puts `[correlationId]` on every line.
4. `correlation-demo/src/test/java/com/example/tracing/CorrelationPropagationTest.java` — one id spans the hop (generated-and-propagated; upstream-reused).
5. `correlation-demo/src/test/java/com/example/tracing/CorrelationIdFilterTest.java` — the filter's generate/reuse/clear behavior.

Note how the propagation interceptor is the *same shape* as topic 16's token propagation — the correlation id and the security context are both "request context" that must travel with every call.

## Try this yourself

1. Run order-service against the scaffold's inventory-service (README) and `curl` it. Note the `X-Correlation-Id` response header and the `[id]` in order-service's logs. Add the same log pattern to inventory-service and confirm both services log the *same* id for one request.
2. Delete the `finally { MDC.remove(...) }` and fire several requests. Watch ids bleed across requests once threads are reused — the classic correlation bug.
3. Read what it would take to switch to Micrometer Tracing: add `micrometer-tracing-bridge-brave`, delete the hand-written filter and interceptor, and change the log pattern to `[%X{traceId},%X{spanId}]`. You'd get spans and Zipkin export for free.

## Self-check

1. Why is finding one request's log lines hard in a distributed system, and how does a propagated correlation id solve it?
2. What are the three things the mechanism does (assign, log, propagate), and why is clearing the MDC in a `finally` block essential?
3. What does distributed tracing (Micrometer Tracing) add on top of a bare correlation id, and which part of it is a microservices concern versus infrastructure that belongs to observability (module 19)?
