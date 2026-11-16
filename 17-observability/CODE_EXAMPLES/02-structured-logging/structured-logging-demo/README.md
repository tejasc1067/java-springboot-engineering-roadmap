# structured-logging-demo

Spring Boot's built-in JSON logging (`logging.structured.format.console: ecs`), the MDC, and the ways request context goes wrong. Tests capture the console with `OutputCaptureExtension` and parse each line as JSON.

- `PlainTextLogTest` — the same events as text: a stack trace over 10+ lines with only one line mentioning the order, and key-value pairs missing.
- `JsonLogFormatTest` — one JSON object per event; key-value pairs and MDC entries become fields; numbers stay numbers; the stack trace is a field.
- `MdcLeakTest` — without `MDC.remove` in `finally`, an anonymous request on the same thread is logged as the previous user.
- `MdcAsyncTest` — an `@Async` method logs on a `task-` thread without the caller's `requestId`.
- `MdcAsyncPropagationTest` (profile `mdc-propagation`) — a `TaskDecorator` bean copies the MDC to the worker thread and removes it afterwards.
- `SensitiveDataTest` — logging a record prints the full card number; a masking `toString` prints `****1111`.

```
mvn -q test
```

No Docker. The JSON lines in the output are the point.

`PlainTextLogTest` uses its own small Logback context: Spring Boot configures Logback once per JVM in tests, so the text format can't be switched on for one test class with a property.
