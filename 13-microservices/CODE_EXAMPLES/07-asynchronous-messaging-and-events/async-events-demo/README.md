# async-events-demo

The behaviours that make asynchronous messaging different from a synchronous call —
with no broker and no Docker. A tiny in-memory queue (`MessageBroker`) stands in for
Kafka/RabbitMQ so `mvn test` runs anywhere.

`AsyncEventsTest` proves, all green:

- **events published while the consumer is down are buffered, then processed** — the
  publisher succeeds with *nobody* consuming; a synchronous call would have failed.
- **publishing does not block on a slow consumer** — enqueuing 3 events returns in
  under a millisecond while each takes 200ms of downstream work; a sync call would
  have blocked ~600ms.
- **sync fails when the downstream is down; async buffers** — the direct call throws
  immediately, the published event waits in the queue for delivery.

## Run

```bash
mvn -q test
```

## What a real broker adds (module 17)

This in-memory queue is lost on restart, is single-process, and has one consumer.
A real broker (Kafka/RabbitMQ) adds durability across restarts, cross-machine
delivery, fan-out to multiple independent consumer groups, and replay of history —
covered in module 17.
