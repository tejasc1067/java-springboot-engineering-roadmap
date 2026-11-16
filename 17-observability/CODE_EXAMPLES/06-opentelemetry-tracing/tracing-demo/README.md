# tracing-demo

Spring Boot with Micrometer Tracing and the OpenTelemetry bridge. Finished spans go to an
`InMemorySpanExporter` so the tests can read them; in production you'd export them over OTLP.

- `SpanTest` — one request is one trace: a server span with a child span inside it; the attributes Spring adds;
  your own attributes and events; an exception turns the spans to `ERROR`; `@Observed` makes a span with no tracing code.
- `PropagationTest` — a `RestClient` from Boot's builder sends `traceparent` and the downstream continues the trace;
  `RestClient.create()` sends nothing and the downstream starts a new trace; an incoming `traceparent` is continued,
  and an upstream "not sampled" flag is respected.
- `AsyncPropagationTest` / `AsyncNoPropagationTest` — `@Async` keeps the trace only with a `ContextPropagatingTaskDecorator`.
- `SamplingTest` — the default probability is 0.1; unsampled requests still have a trace id but export nothing.

Run (no Docker):

```
mvn -q test
```

One test logs an `IllegalArgumentException: no such sku: unknown-1` stack trace on purpose.
