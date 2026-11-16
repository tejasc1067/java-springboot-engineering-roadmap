# Spring Kafka Producer

Sending a record with Spring is one line: `kafkaTemplate.send(topic, key, value)`. Most producer bugs come from not knowing what that line actually promises. It doesn't wait for Kafka, so an error can happen after it returns, and nobody hears about it. The key decides the partition and so the ordering. And `acks` decides how many copies exist before Kafka says "done". This topic covers each one with a test.

The demo is a Spring Boot app with `@EmbeddedKafka` (no Docker).

---

## The problem this solves

order-service publishes `OrderPlaced` after saving an order. Three things must be true for that event to be useful:

1. It must actually arrive, or the email, the warehouse, and analytics never hear about the order.
2. Events for the same customer must stay in order.
3. Once Kafka has accepted it, a broker failure mustn't lose it.

Each of those is a producer setting or a coding habit, and the defaults only get you part of the way.

## How it works

### `KafkaTemplate` and serializers

Spring Boot creates a `KafkaTemplate` from `spring.kafka.*` properties. Kafka stores bytes, so the producer needs a **serializer** for the key and one for the value:

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

```java
public record OrderPlaced(String orderId, String customerId, long totalCents) { }

kafka.send("orders", event.customerId(), event);    // topic, key, value
```

`JsonSerializationTest` reads the record back as plain text, to show what Kafka really holds:

```text
key:     customer-json
value:   {"orderId":"o-json","customerId":"customer-json","totalCents":4250}
header:  __TypeId__ = com.example.producer.OrderPlaced
```

`JsonSerializer` adds the Java class name as a header, which Spring's `JsonDeserializer` can use on the other side. That header ties consumers to the producer's class names. Topic 09 covers why, and the schema choices that avoid it.

### Keys choose the partition

For a record with a key, the default partitioner computes:

```java
partition = Utils.toPositive(Utils.murmur2(keyBytes)) % numberOfPartitions
```

`thePartitionIsAHashOfTheKey_moduloThePartitionCount` checks that formula against the partition Kafka reported for four customers, and `sameKey_alwaysLandsOnTheSamePartition` sends five orders for `customer-42`: all five land in one partition. Since Kafka orders only within a partition (topic 02), **the key is how you get ordering**: key by the thing whose events must stay in order (customer id, order id, account id).

A record with **no key** goes to whichever partition the producer is currently filling a batch for, so related records end up anywhere. Topic 05 shows the out-of-order bug that causes, and why adding partitions later breaks the formula above.

### `send()` doesn't wait

`send()` puts the record in a buffer and returns a `CompletableFuture<SendResult>`. A background thread batches records and sends them to the broker. The broker's answer arrives later and completes the future.

#### Broken: fire and forget

```java
public void publishAndForget(OrderPlaced event) {
    kafka.send(TOPIC, event.customerId(), event);      // the future is thrown away
}
```

`FireAndForgetTest` sends an order that's 20 kB. The producer accepts it (its own limit, `max.request.size`, is 1 MB), but this broker only accepts 10 kB records (`message.max.bytes`, lowered in the test's broker so the record can be small). Only the broker can say no, and it says it after `send()` has returned:

- **`ignoringTheFuture_theFailureIsSilent`**: `publishAndForget` returns normally. The caller commits its database transaction and returns `201 Created`. The event never exists.
- **`theFutureKnew_itCompletedExceptionally`**: the future completed with `RecordTooLargeException`.

The only trace is an `ERROR` line in the log from Spring's `LoggingProducerListener`. Anything that makes the broker refuse or never answer goes the same way: too few in-sync replicas (below), a broker that stays unreachable past the timeout, an authorization error.

Some errors don't wait. **`errorsTheClientFindsItself_areThrownStraightFromSend`**: a 2 MB record is over the *producer's* own limit, so `send()` throws `KafkaException: Send failed` immediately. That's why fire-and-forget code can look safe in a quick test: the obvious failures throw, and the broker-side ones are silent.

#### Fixed: handle the future

Wait for the answer when the caller needs to know:

```java
public SendResult<String, Object> publish(OrderPlaced event) throws Exception {
    return kafka.send(TOPIC, event.customerId(), event).get(10, TimeUnit.SECONDS);
}
```

`waitingForTheResult_theCallerSeesTheError` gets the `RecordTooLargeException`, so the request fails instead of pretending. And `aSuccessfulSend_tellsYouWhereTheRecordWent`: a successful `SendResult` carries the topic, partition, and offset.

Or don't block, and decide what failure means in a callback:

```java
kafka.send(TOPIC, key, event).whenComplete((result, error) -> {
    if (error != null) {
        // retry later, mark the order "event pending", alert - but don't ignore it
    }
});
```

Waiting costs a round trip per send (milliseconds). For high-volume sends, use the callback. Either way, a database write followed by a Kafka send can still lose the event if the process dies between them; the full fix is the **transactional outbox** (module 13 topic 19).

### `acks`: how many copies before "done"

When the leader receives a record, the `acks` setting decides when it replies:

| `acks` | The producer is told "done" when | A crash right after can lose the record? |
|---|---|---|
| `0` | immediately, without waiting for the broker at all | yes, and you'd never know |
| `1` | the leader has written it | yes, if the leader dies before a follower copies it |
| `all` (`-1`) | every in-sync replica has it | no, as long as one in-sync replica survives |

`acks=all` alone has a gap: if the followers have fallen out of the ISR, "every in-sync replica" might be just the leader. **`min.insync.replicas`**, a topic or broker setting, closes it: an `acks=all` write is refused unless at least that many copies are in sync.

`MinInSyncReplicasTest` runs two brokers and a `payments` topic with replication factor 2 and `min.insync.replicas=2`:

1. Both brokers up: an `acks=all` write succeeds.
2. Broker 1 is shut down. The partition still has its leader, but the ISR shrinks to 1.
3. An `acks=all` write is **refused**. The broker answers `NotEnoughReplicasException`, which is retriable, so the producer keeps retrying until `delivery.timeout.ms` (2 minutes by default, 3 seconds in the test) and then fails the future with `TimeoutException: Expiring 1 record(s)`. A producer with retries turned off shows the broker's real answer, `NotEnoughReplicasException`.
4. An `acks=1` write is **accepted**: the leader alone has it. If that broker's disk dies now, the payment is gone.

So `acks=all` with `min.insync.replicas` trades availability for durability: with too few copies, Kafka refuses writes rather than accept ones it might lose. The common production setting is replication factor 3, `min.insync.replicas=2`, `acks=all`: one broker can be down and writes still succeed.

One surprise found while building the demo: with a single broker and `min.insync.replicas=2`, Kafka accepted `acks=all` writes. A `min.insync.replicas` larger than the replication factor isn't enforced as written. Set the two together.

### The idempotent producer

When a send times out, the producer retries. But a timeout doesn't mean the write failed: maybe the broker wrote the record and the reply was lost. A retry would then write it twice.

The **idempotent producer** prevents that. The producer gets an id and numbers every batch per partition; the broker remembers the last numbers it accepted and drops a retried batch it has already written. It also keeps retries from reordering records. It covers **duplicates caused by the producer's own retries** only: if your code calls `send()` twice for the same event, that's two records (topic 08 handles that on the consumer side).

`AcksAndIdempotenceTest` checks the client's rules:

- **`theDefaults_areAcksAll_andIdempotenceOn`**: since Kafka 3.0, a producer with no settings uses `acks=all` and `enable.idempotence=true`.
- **`settingAcks1_silentlyTurnsIdempotenceOff`**: idempotence needs `acks=all`. Set `acks=1` "for speed" and idempotence quietly switches off, along with its duplicate protection.
- **`askingForIdempotenceWithAcks1_isRejected`**: ask for both explicitly and the producer won't start (`ConfigException`).

Leave the defaults alone unless you have measured a reason.

---

## When to use it / when not to

- **Wait for the send** (`.get(timeout)`) when the caller must know the event was accepted before replying: low-volume, important events.
- **Use the callback** for high-volume sends, but always handle the failure: retry, record it, alert.
- **Use the outbox** (module 13 topic 19) when the event must be published if and only if a database change commits.
- **`acks=1` or `0`** only for data you can afford to lose: metrics, logs, clickstream. Never for orders or payments.

---

## Common pitfalls

- **Ignoring the future from `send()`.** Broker-side failures disappear into a log line. Wait on it or handle it in `whenComplete`.
- **No key, or the wrong key.** Related events spread over partitions and arrive out of order. Key by the entity whose events must stay ordered.
- **Lowering `acks` for throughput.** It also turns off idempotence. Tune `linger.ms`, `batch.size`, and compression first.
- **`min.insync.replicas` equal to the replication factor.** With RF 3 and min 3, any single broker restart stops all writes. Use RF 3, min 2.

---

## Code examples

`CODE_EXAMPLES/03-spring-kafka-producer/kafka-producer-demo/`. Run with `mvn -q test` (no Docker; about 15 seconds). Read in this order:

1. `src/main/java/com/example/producer/OrderPlaced.java` — the event.
2. `src/main/java/com/example/producer/OrderPublisher.java` — `publishAndForget` (broken), `publish` and `publishAsync` (fixed).
3. `src/main/resources/application.yml` — the serializers.
4. `src/test/java/com/example/producer/KeysAndPartitionsTest.java` — same key, same partition; the hash formula.
5. `src/test/java/com/example/producer/JsonSerializationTest.java` — the bytes and the type header Kafka actually stores.
6. `src/test/java/com/example/producer/FireAndForgetTest.java` — the silent broker-side failure; the future that knew; the client-side error that throws.
7. `src/test/java/com/example/producer/AcksAndIdempotenceTest.java` — the defaults, and `acks=1` switching idempotence off.
8. `src/test/java/com/example/producer/MinInSyncReplicasTest.java` — a broker goes down; `acks=all` is refused and `acks=1` isn't.

---

## Try this yourself

1. Change `OrderPublisher.publishAndForget` to use `whenComplete` and count failures in an `AtomicInteger`. Make `ignoringTheFuture_theFailureIsSilent` assert the count reaches 1 instead.
2. In `MinInSyncReplicasTest`, set `min.insync.replicas` to 1 and run it. Which assertion fails, and what does that tell you about `acks=all` on its own?

## Self-check

1. `kafkaTemplate.send(...)` returned without an exception. What do you actually know at that point? Give one failure that would still be reported later, and one that would have thrown immediately.
2. What do `acks=all` and `min.insync.replicas=2` each guarantee, and what does a producer experience when only one replica is in sync?
3. Which duplicates does the idempotent producer prevent, and which ones can it not?
