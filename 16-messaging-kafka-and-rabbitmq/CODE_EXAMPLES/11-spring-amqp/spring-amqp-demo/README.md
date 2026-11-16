# spring-amqp-demo

A Spring Boot service consuming from RabbitMQ with `@RabbitListener`, against a real broker
(`rabbitmq:4.1-management-alpine`, started by Testcontainers).

- `JavaSerializationConverterTest` — the default converter rejects a plain record, and the receiver refuses a
  Java-serialized one.
- `JsonConverterTest` — `Jackson2JsonMessageConverter`: readable JSON, plus a `__TypeId__` header.
- `PoisonMessageTest` — a failing listener with default settings redelivers one message 100 times in under a second;
  a RabbitMQ 4 quorum queue gives up after 20 redeliveries.
- `DeadLetterTest` — `default-requeue-rejected=false` and a dead-letter exchange: one attempt, then the DLQ.
- `RetryThenDeadLetterTest` — three in-listener attempts, then the DLQ.
- `PublisherConfirmsTest` — ack for a routed message, ack *and* a return for an unroutable one, nack for a missing
  exchange.

Run (Docker running):

```
mvn -q test
```

About 15 seconds; each test starts a fresh application context so no listener or counter leaks between tests. One
`WARN ... RejectAndDontRequeueRecoverer : Retries exhausted` with a stack trace is expected: that's the retry test
giving up.
