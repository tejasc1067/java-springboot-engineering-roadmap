# Correlating Logs and Traces

Logs, metrics, and traces (topic 01) are most useful together: an alert on a metric points to a trace, and the trace id finds every log line of that request in every service. That only works if the same id shows up in all three places, and in the error a customer sees. This topic is about putting it there, and the places where it quietly goes missing: a background thread, and the stack trace of an unhandled exception, the one line you need most.

The demo needs no Docker. Tests capture the console output and check the log lines.

---

## The problem this solves

A customer reports "my order page broke at 14:02". You open the logs: 40,000 lines from that minute, from 12 instances. Without an id that ties a line to a request, you're guessing from timestamps. With one, it's a single search: `traceId:"4eb3d374..."`, across every service, in order.

The same id should be in four places:

1. **Every log line** written while handling the request, on every thread, in every service.
2. **The trace** (topic 06), where it's the trace id itself.
3. **The metrics**, as an example attached to a slow or failed request (an *exemplar*).
4. **The response**, especially an error, so a customer or a support ticket can quote it.

## How it works

### Trace ids in every log line

Add tracing (topic 06) and Spring Boot does the rest. During a request, the tracer puts `traceId` and `spanId` in the SLF4J MDC (module 13 topic 17), and Boot's default console pattern prints them. `LogCorrelationTest.everyLogLineInTheRequest_carriesTheTraceIdAndSpanId`:

```text
2026-11-16T18:06:30.496+05:30  INFO 18146 --- [orders] [o-auto-1-exec-1] [1ebf7c790363d9b66aeee391f8b5362a-79664b702f108095] c.e.logcorrelation.OrderController : loading order 42
                                                    app name    thread       traceId                          spanId
```

Outside a request, such as at startup, the brackets are filled with spaces. You don't change the log pattern for this; Boot adds the trace part when tracing is on.

With structured logs (topic 02), the ids are separate fields, which is what a log search tool needs. `StructuredLogTest.jsonLogs_carryTraceIdAndSpanId_asSearchableFields`, with `logging.structured.format.console=logstash`:

```json
{"message":"loading order 50", "thread_name":"http-nio-auto-1-exec-1", "level":"INFO",
 "traceId":"37ad158468b5eb32301f343bd258abdd", "spanId":"bc0476da1ee200a5", "orderId":"50", "customerId":"c-50"}
```

The `ecs` format puts them in the same top-level `traceId` and `spanId` fields.

### Broken: a background thread logs without the id

The MDC is per thread. Work handed to a thread pool runs without the request's trace, so its log lines can't be found by trace id. `aPlainThreadPool_logsWithAnEmptyTraceId`:

```text
... [orders] [        plain-1] [                                                 ] ... : emailing receipt (plain pool)
```

The fix is the one from topic 06: a `ContextPropagatingTaskDecorator` on the executor. The tracer restores the trace on the worker thread, and with it the MDC entries (`aPoolWithContextPropagatingTaskDecorator_logsTheRequestsTraceId`):

```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
```

```text
... [orders] [       traced-1] [1ebf7c790363d9b66aeee391f8b5362a-79664b702f108095] ... : emailing receipt (traced pool)
```

**Only the trace context travels, not your own MDC keys.** `StructuredLogTest.onAnotherThread_theTraceAndBaggageArrive_butYourOwnMdcKeysDoNot`: the controller does `MDC.put("orderId", id)`, and the line from the traced pool has `traceId` but no `orderId`. If a value must follow the request, make it **baggage**, which is part of the trace context:

```java
try (BaggageInScope customer = tracer.createBaggageInScope("customerId", "c-" + id)) {
    ...
}
```

```yaml
management:
  tracing:
    baggage:
      remote-fields: customerId      # also sent to other services, in a "customerId" header
      correlation:
        fields: customerId           # copied into the MDC, so it shows up in log lines
```

The traced pool's line has `"customerId":"c-51"`. Baggage costs a header on every outgoing call, so keep it to a few small values, and never secrets.

### Broken: the error you need has no id

The worst gap is the one you'd least expect. An exception nobody handles goes up through Spring MVC to Tomcat, which logs the stack trace. By then Spring's observation filter has finished and the request's span is no longer current. `anUnhandledException_theStackTraceLineHasNoTraceId_andNeitherDoesTheResponse`:

```text
... [o-auto-1-exec-4] [d8611bf0eb5772f8560592ca7e274d23-f12071031eed568b] ... : loading order crash-1
... [o-auto-1-exec-4] [                                                 ] ... : Servlet.service() for servlet [dispatcherServlet] ... threw exception [Request processing failed: java.lang.IllegalStateException: database unavailable] with root cause
```

The request had a trace id, and the `X-Trace-Id` response header shows it, but the ERROR line with the stack trace doesn't. Searching by trace id finds "loading order" and never the error. And Boot's default error body has no id for the customer to quote:

```json
{"timestamp":"...","status":500,"error":"Internal Server Error","path":"/orders/crash-1"}
```

### Fixed: handle every exception while the span is current

A `@RestControllerAdvice` runs inside the request, while the span is still current. Handle your own exceptions there with a `ProblemDetail` (module 11 topic 05) and add the trace id (`ErrorResponseTest`):

```java
@ExceptionHandler(OrderNotFoundException.class)
ProblemDetail notFound(OrderNotFoundException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setProperty("traceId", tracer.currentSpan().context().traceId());
    return problem;
}
```

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"order missing-1 not found",
 "instance":"/orders/missing-1","traceId":"0c9cbd0c34fe26ce3d61ae31f873307f"}
```

Then add a last-resort handler for everything else. It logs the exception itself, so the stack trace is written with the trace id, and returns the id without leaking internals (`CatchAllErrorTest`):

```java
@ExceptionHandler(Exception.class)
ProblemDetail unexpected(Exception e) {
    log.error("request failed", e);   // logged while the span is current: has the trace id
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong. Quote the traceId when contacting support.");
    problem.setProperty("traceId", tracer.currentSpan().context().traceId());
    return problem;
}
```

Now the ERROR line has `[<traceId>-<spanId>]`, Tomcat logs nothing, and the response body carries the id but not "database unavailable".

The demo also returns the id on every response as an `X-Trace-Id` header, from a small filter that runs inside Spring's observation filter. Set the header *before* calling the rest of the chain: once the body has been written, headers can't be added.

### Exemplars: from a metric to a trace

A latency histogram (topic 04) tells you that some requests to `/orders/{id}` took over 2 seconds. An **exemplar** attaches the trace id of one actual request to the bucket it fell into, so a dashboard (topic 05) can link straight from the spike to a trace.

With tracing and `micrometer-registry-prometheus` on the classpath, Boot adds exemplars by itself. They appear only in the **OpenMetrics** format, which Prometheus asks for when it scrapes, and only on histograms (each bucket line and the `_count` line), so the histogram must be on (`management.metrics.distribution.percentiles-histogram.http.server.requests=true`). `ExemplarTest`:

```text
http_server_requests_seconds_bucket{...,status="200",uri="/orders/{id}",le="0.033554431"} 1 # {span_id="e47b0348dac6bc26",trace_id="1b03041175e014efc4db5f012a7c27ed"} 0.0331345 1794832601.226
                                                                                           exemplar: which request, its value, when
```

The plain Prometheus text format (the default when you `curl` the endpoint) has no exemplars at all. Exemplars are samples, not a complete list: each bucket keeps one recent example. And only sampled traces (topic 06) can be found in the tracing backend.

---

## An incident, start to finish

1. **Alert** (topic 09): the error rate for `/orders/{id}` is over budget.
2. **Dashboard** (topic 05): the 5xx panel spikes at 14:02. The exemplar on the spike links to trace `d8611bf0...`.
3. **Trace** (topic 06): the waterfall shows the `/orders/{id}` span in `ERROR` after 30 ms, and the database span inside it failed.
4. **Logs**: search `traceId:"d8611bf0..."` across all services. You get "loading order crash-1", the ERROR line with the stack trace (because the catch-all handler logged it), and any lines from background threads (because the executor propagates context).
5. **Customer**: their error response said `"traceId":"d8611bf0..."`, so support could have started at step 4 directly.

Every step depends on the id being present. Each broken case in this topic removes one step.

## When to use it / when not to

- **Always** put trace ids in logs once tracing is on. Boot does it by default; keep it that way in custom patterns (`%X{traceId}`) and structured formats.
- **Return the trace id in error responses** of any API used by people or other teams. It's not sensitive: it identifies a request, not a user.
- **Exemplars** need histograms, the OpenMetrics format, a Prometheus that stores them (started with `--enable-feature=exemplar-storage`), and a tracing backend to open the trace they point to. Turn them on where you already have latency histograms.
- **Baggage** for a few small values that genuinely need to follow the request (tenant, customer id). Not for large or secret data: it's sent on every outgoing call.

## Common pitfalls

- **Background work without context propagation**: its log lines have no trace id. Decorate every executor.
- **Unhandled exceptions logged by the server**: the stack trace has no trace id. Add a catch-all `@ExceptionHandler` that logs and returns the id.
- **Expecting `MDC.put` values on other threads**: they don't follow. Use baggage with `correlation.fields`.
- **A custom log pattern without the trace id**: someone replaces `logging.pattern.console` and drops `%X{traceId}`. Check the pattern after changing it.
- **Looking for exemplars with `curl`**: the default text format never contains them. Request OpenMetrics.

---

## Code examples

`CODE_EXAMPLES/07-correlating-logs-and-traces/logcorrelation-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/main/resources/application.yml`: sampling, baggage, the histogram for exemplars.
2. `src/main/java/com/example/logcorrelation/OrderController.java`: logs on the request thread and on two pools; `MDC.put` and baggage.
3. `src/main/java/com/example/logcorrelation/LogCorrelationApplication.java`: the plain pool and the decorated pool.
4. `src/test/java/com/example/logcorrelation/LogTestBase.java`: requests, captured output, finding a log line.
5. `src/test/java/com/example/logcorrelation/LogCorrelationTest.java`: trace ids in log lines, the background thread, the unhandled exception.
6. `src/main/java/com/example/logcorrelation/TraceIdHeaderFilter.java` and `ErrorHandler.java`, then `src/test/java/com/example/logcorrelation/ErrorResponseTest.java`: the id in the header and the `ProblemDetail`.
7. `src/main/java/com/example/logcorrelation/CatchAllErrorHandler.java` and `src/test/java/com/example/logcorrelation/CatchAllErrorTest.java`: the last-resort handler.
8. `src/test/java/com/example/logcorrelation/StructuredLogTest.java`: JSON fields, baggage vs MDC on another thread.
9. `src/test/java/com/example/logcorrelation/ExemplarTest.java`: exemplars in OpenMetrics, none in plain text.

---

## Try this yourself

1. Set `logging.structured.format.console=ecs` in `StructuredLogTest` instead of `logstash`. Which assertions still pass, and how is the stack trace of an error laid out?
2. Remove the `@Profile("catch-all")` line from `CatchAllErrorHandler` and run `LogCorrelationTest`. Which test fails, and why is that the result you want?
3. Add `orderId` to `management.tracing.baggage.correlation.fields` and replace `MDC.put("orderId", id)` with a second `createBaggageInScope`. Does the traced pool's log line have `orderId` now?

## Self-check

1. A request's first log lines have a trace id but its ERROR stack trace doesn't. Why, and what change fixes it?
2. Why does a log line from a `@Async` method lose its trace id, and why doesn't a value you put in the MDC yourself come back even after you fix that?
3. What is an exemplar, where does it appear, and what do you need for Prometheus to keep it?
