# retry-dlt-demo

What happens to a record whose listener keeps failing.

- `DefaultErrorHandlerTest` — nothing configured: 10 attempts with no delay, then an ERROR log line and the record is skipped.
- `InfiniteRetryTest` — `FixedBackOff(10, UNLIMITED_ATTEMPTS)`: one poison record blocks every record behind it.
- `DeadLetterTopicTest` — `DeadLetterPublishingRecoverer` + 3 attempts: the record goes to `orders-dlt` with
  `kafka_dlt-*` headers; `IllegalArgumentException` is not retried; after a fix, dead letters are replayed.
- `DeserializationErrorBrokenTest` — `JsonDeserializer` on its own: bad JSON fails inside `poll()` over and over,
  and the good record behind it never arrives.
- `DeserializationErrorTest` — wrapped in `ErrorHandlingDeserializer`: bad JSON goes to `shipments-dlt` on the first try.
- `RetryTopicTest` — `@RetryableTopic`: `payments-retry` and `payments-dlt` are created; a retried record is overtaken.

Listeners are switched on per test with profiles (`shipments`, `shipments-broken`, `retry-topics`).

Run (no Docker):

```
mvn -q test
```

The output has many ERROR lines and stack traces. They're the failures the tests provoke.
