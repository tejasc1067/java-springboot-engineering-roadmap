# Structured Logging

Module 08 topic 10 set up logging for a person reading a terminal. In production nobody reads a terminal: logs from dozens of instances flow into a log platform (Elasticsearch, Loki, Splunk, a cloud log service), and you *query* them: "every ERROR for user ann in the last hour", "all events for order-2". **Structured logging** writes each event as one JSON object with named fields, so those queries work. This topic covers Spring Boot's built-in JSON logging, the MDC that adds request context to every line, and the three ways that context goes wrong: leaking between requests, disappearing on other threads, and carrying data that should never be logged.

---

## The problem this solves

A text log line is a sentence:

```text
2026-11-16T18:02:45.589 ERROR [main] c.e.logging.OrderService : payment failed for order order-2
java.lang.IllegalStateException: card declined
	at com.example.logging.OrderService.failPayment(OrderService.java:23)
	at ...30 more lines...
```

To find it you `grep "order-2"`. That gets you one line. The exception and its stack trace are on the following lines, which don't mention `order-2`, so a log platform that splits on newlines stores them as separate, unrelated events. "All errors for user ann" is impossible unless every message happens to contain the user. And any query that parses the text breaks the day someone rewords the message.

`PlainTextLogTest.anErrorIsSpreadOverManyLines_andOnlyTheFirstOneSaysWhichOrder` shows it: more than 10 stack-trace lines, and only one line containing `order-2`.

## How it works

### JSON logs with one property

Spring Boot 3.4+ has structured logging built in. No extra library:

```yaml
spring:
  application:
    name: order-service
logging:
  structured:
    format:
      console: ecs   # also: logstash, gelf
```

Now every event is one line of JSON, here in ECS (Elastic Common Schema) field names:

```json
{"@timestamp":"2026-11-16T12:34:59.948390Z",
 "log":{"level":"ERROR","logger":"com.example.logging.OrderService"},
 "process":{"pid":17507,"thread":{"name":"main"}},
 "service":{"name":"order-service"},
 "message":"payment failed for order order-2",
 "userId":"ann",
 "error":{"type":"java.lang.IllegalStateException","message":"card declined",
          "stack_trace":"java.lang.IllegalStateException: card declined\n\tat com.example.logging.OrderService.failPayment..."},
 "ecs":{"version":"8.11"}}
```

(Shown on several lines here for reading. In the output it's one line.)

`JsonLogFormatTest.anErrorWithItsStackTrace_isStillOneLine`: the stack trace is a field inside the event, so the whole error is one line and one record. The log platform can now answer `log.level:ERROR AND userId:ann` directly.

`ecs`, `logstash`, and `gelf` are the three formats Boot ships. Pick the one your log platform expects; they differ only in field names. `logging.structured.format.file` does the same for a log file.

### Fields, not sentences

Put data in fields, not inside the message text. SLF4J 2's fluent API adds key-value pairs:

```java
log.atInfo()
        .setMessage("order placed")
        .addKeyValue("orderId", orderId)
        .addKeyValue("totalCents", totalCents)
        .log();
```

```json
{"message":"order placed","orderId":"order-1","totalCents":1999, ...}
```

`JsonLogFormatTest.eachEventIsOneJsonObject_withFieldsAQueryCanUse`: `totalCents` arrives as a JSON number, so `totalCents > 10000` is a real query. The message stays fixed (`order placed`), so you can count events by message.

One catch: Spring Boot's default *text* pattern prints only the message, not key-value pairs. `PlainTextLogTest.keyValuePairs_areNotInATextPattern`: the `orderId` is simply missing. If some environments use text logs, put important ids in the message too, or add `%kvp` to the text pattern.

### The MDC: context on every line

You don't want to pass `userId` to every log call. The **MDC** (Mapped Diagnostic Context, module 13 topic 17) is a per-thread map that Logback adds to every event on that thread. With JSON logs, each MDC entry becomes a field:

```java
MDC.put("userId", "ann");
orders.placeOrder("order-1", 1999);   // {"message":"order placed","userId":"ann", ...}
orders.failPayment("order-2");        // {"message":"payment failed ...","userId":"ann", ...}
```

`mdcEntries_becomeFieldsOnEveryEvent`. Typically a servlet filter puts the request id and user id into the MDC at the start of each request. With Micrometer Tracing on the classpath, Spring Boot also puts `traceId` and `spanId` there for you (topic 07).

### Broken: the MDC leaks into the next request

The MDC is stored per **thread**, and a web server reuses a small pool of threads for all requests. A filter that puts a value and never removes it:

```java
public void handleBroken(String userId, String path) {
    if (userId != null) MDC.put("userId", userId);
    log.info("handling {}", path);
}
```

`MdcLeakTest.withoutClearing_theNextRequestOnTheSameThreadIsLoggedAsTheLastUser` runs two requests on one thread, ann's and then an anonymous one:

```json
{"message":"handling /public/catalog","userId":"ann", ...}
```

The anonymous request is logged as ann. During an investigation that's worse than no user id: the logs confidently say the wrong thing.

### Fixed: remove in `finally`

```java
public void handle(String userId, String path) {
    if (userId != null) MDC.put("userId", userId);
    try {
        log.info("handling {}", path);
    } finally {
        MDC.remove("userId");
    }
}
```

`clearingInFinally_theNextRequestStartsClean`. `try (var ignored = MDC.putCloseable("userId", userId)) { ... }` does the same in one line.

### Broken: the MDC is lost on another thread

The other direction: work handed to another thread doesn't take the MDC with it.

```java
@Async
public CompletableFuture<Void> sendReceipt(String orderId) {
    log.info("receipt sent for {}", orderId);
    ...
}
```

`MdcAsyncTest.asyncWork_losesTheCallersMdc`: the caller has `requestId=req-7`, but the event logged on thread `task-1` has no `requestId`. The same happens with `CompletableFuture.supplyAsync`, your own executors, and scheduled work. The receipt can no longer be tied to the request that caused it.

### Fixed: a `TaskDecorator`

A `TaskDecorator` wraps every task submitted to an executor. It runs first on the caller's thread (to copy the MDC) and then on the worker (to install it, and restore afterwards):

```java
@Bean
TaskDecorator mdcTaskDecorator() {
    return task -> {
        Map<String, String> callers = MDC.getCopyOfContextMap();   // caller's thread
        return () -> {                                             // worker thread
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (callers != null) MDC.setContextMap(callers); else MDC.clear();
            try {
                task.run();
            } finally {
                if (previous != null) MDC.setContextMap(previous); else MDC.clear();
            }
        };
    };
}
```

Spring Boot applies a `TaskDecorator` bean to the executor it creates for `@Async`, so declaring the bean is enough. `MdcAsyncPropagationTest`: the receipt event now has `requestId=req-7`, and a later task without a request id doesn't inherit it. For executors you create yourself, call `setTaskDecorator` on them. Spring Framework also ships `ContextPropagatingTaskDecorator`, which carries any context registered with Micrometer's context-propagation library, the trace context included.

### Broken: logging a whole object

```java
public record RawCard(String holder, String number) { }

log.info("charging {}", card);   // charging RawCard[holder=Ann, number=4111111111111111]
```

A record's generated `toString` prints every field. `SensitiveDataTest.loggingAWholeObject_logsEveryFieldInIt`: the full card number is in the log. Logs are copied to many systems, kept for months, and read by many people; a card number, password, token, or other personal data in them is a security incident and, under laws like GDPR or PCI DSS rules, a compliance one.

### Fixed: log ids, or mask

```java
public record Card(String holder, String number) {
    @Override
    public String toString() {
        return "Card[holder=" + holder + ", number=****" + number.substring(number.length() - 4) + "]";
    }
}
```

`aMaskingToString_keepsTheNumberOutOfTheLogs`: `number=****1111`. Better still, log an id (`cardId`, `customerId`) instead of the object. Be most careful with request and response bodies, headers (`Authorization`), and exception messages that include input.

---

## When to use it / when not to

- **JSON logs whenever logs leave the machine** for a log platform, which is every production deployment. Locally, the text format is easier to read: set the structured format in a production profile (module 08 topic 07) if your team prefers.
- **Log events, not traces of execution.** One line when an order is placed, a payment fails, a retry gives up. Not one per loop iteration. Log volume costs money and buries the lines that matter.
- **Levels in production:** `INFO` for your code, `WARN` for libraries. Raise a single logger to `DEBUG` temporarily through Actuator's `loggers` endpoint (module 08 topic 12) instead of redeploying.
- **Don't count log lines to make a graph.** Use a metric (topic 03).
- **Use parameterized messages** (`log.info("order {}", id)`), never string concatenation (module 08 topic 10).

## Common pitfalls

- **`MDC.put` without `MDC.remove` in `finally`.** The next request on that thread inherits the value. Use `try/finally` or `MDC.putCloseable`.
- **Expecting the MDC on `@Async` or executor threads.** It's per thread. Add a `TaskDecorator`.
- **Logging whole objects, request bodies, or headers.** Secrets and personal data end up in the logs. Log ids, mask the rest.
- **Ids only in key-value pairs while some environments log text.** The default text pattern drops them. Add `%kvp` or keep ids in the message as well.
- **Changing log messages that dashboards or alerts search for.** Fields are a stable contract; message text isn't. Query on fields.

---

## Code examples

`CODE_EXAMPLES/02-structured-logging/structured-logging-demo/`. Run with `mvn -q test` (no Docker). Read in this order:

1. `src/main/resources/application.yml`: `logging.structured.format.console: ecs`.
2. `src/main/java/com/example/logging/OrderService.java`: an event with key-value pairs, and an error with a stack trace.
3. `src/test/java/com/example/logging/PlainTextLogTest.java`: the same events as text: a stack trace over many lines, key-value pairs missing.
4. `src/test/java/com/example/logging/JsonLines.java` and `JsonLogFormatTest.java`: one JSON object per event; fields, numbers, MDC entries, the stack trace inside the event.
5. `src/main/java/com/example/logging/RequestHandler.java` and `src/test/java/com/example/logging/MdcLeakTest.java`: the MDC leaking into the next request, and the `finally` that fixes it.
6. `src/main/java/com/example/logging/ReceiptService.java` and `src/test/java/com/example/logging/MdcAsyncTest.java`: the MDC lost on an `@Async` thread.
7. `src/main/java/com/example/logging/MdcPropagationConfig.java` and `src/test/java/com/example/logging/MdcAsyncPropagationTest.java`: the `TaskDecorator` that carries it over.
8. `src/main/java/com/example/logging/CardLogging.java` and `src/test/java/com/example/logging/SensitiveDataTest.java`: a card number in the logs, and the masked version.

---

## Try this yourself

1. Change `application.yml` to `console: logstash` and run `JsonLogFormatTest`. Which assertions fail, and what are the Logstash names for the level and the logger?
2. Replace `RequestHandler.handle`'s `try/finally` with `MDC.putCloseable` in a try-with-resources. Does `MdcLeakTest` still pass?
3. In `MdcAsyncTest`, call `CompletableFuture.runAsync(() -> log.info(...))` instead of the `@Async` method, with the `mdc-propagation` profile active. Is the `requestId` there? Why not?

## Self-check

1. Why does `grep "order-2"` miss most of an error in a text log, and how does a JSON log fix it?
2. An anonymous request shows up in the logs as user `ann`. What's the bug, and what's the one-line fix?
3. Why is the MDC missing in `@Async` methods, and how does a `TaskDecorator` bring it back?
