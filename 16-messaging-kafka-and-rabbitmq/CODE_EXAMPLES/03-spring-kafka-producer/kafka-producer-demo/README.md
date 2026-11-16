# kafka-producer-demo

`KafkaTemplate` against embedded Kafka brokers: keys and partitions, fire-and-forget, `acks`, `min.insync.replicas`,
and the idempotent producer.

- `KeysAndPartitionsTest` — the same key always lands on the same partition; the partition is
  `murmur2(key) % partitions`.
- `JsonSerializationTest` — what `JsonSerializer` really stores: JSON bytes plus a `__TypeId__` header.
- `FireAndForgetTest` — a record the broker refuses (over its 10 kB `message.max.bytes`, lowered for the test):
  ignoring the future, nobody knows; the future completed with `RecordTooLargeException`; waiting on it surfaces
  the error. A record over the producer's own 1 MB limit throws straight from `send()`.
- `AcksAndIdempotenceTest` — the defaults are `acks=all` and idempotence on; `acks=1` switches idempotence off;
  asking for both is a `ConfigException`.
- `MinInSyncReplicasTest` (2 brokers) — replication factor 2, `min.insync.replicas=2`. One broker is shut down:
  `acks=all` is refused (`TimeoutException` after retrying `NotEnoughReplicasException`), `acks=1` is accepted.

Run (no Docker):

```
mvn -q test
```

About 15 seconds. Expect `ERROR ... LoggingProducerListener - Exception thrown when sending a message` lines: that
log line is the only trace a fire-and-forget failure leaves, and the tests trigger it on purpose.
