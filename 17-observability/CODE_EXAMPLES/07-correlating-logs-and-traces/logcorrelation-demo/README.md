# logcorrelation-demo

Getting from an alert to the exact log lines of one request: trace ids in logs, in error responses, and in metrics.

- `LogCorrelationTest` — Boot's default log line carries `[traceId-spanId]`; a plain thread pool logs an empty id,
  a pool with `ContextPropagatingTaskDecorator` logs the request's id; an unhandled exception's stack trace is logged
  by Tomcat with **no** trace id, and Boot's default error body has none either.
- `ErrorResponseTest` — a `ProblemDetail` with the trace id, matching the `X-Trace-Id` header and the log lines.
- `CatchAllErrorTest` (profile `catch-all`) — a last-resort `@ExceptionHandler` logs the error while the span is
  still current and returns the id.
- `StructuredLogTest` — JSON logs (`logstash` format): `traceId` and `spanId` are fields; baggage reaches other
  threads, your own `MDC.put` keys don't.
- `ExemplarTest` — `/actuator/prometheus` in OpenMetrics format attaches a trace id to latency buckets; the plain
  Prometheus text format doesn't.

Run (no Docker):

```
mvn -q test
```

Each test class runs in its own JVM (see `pom.xml`). One test logs an `IllegalStateException: database unavailable`
stack trace on purpose.
