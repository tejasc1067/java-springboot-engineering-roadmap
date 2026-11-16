# Common mistakes — Messaging with Kafka and RabbitMQ

Real messaging bugs, each one reproduced by a test in this module. Messaging bugs are quiet by nature: the producer returns, the broker acks, the listener logs one line, and a message is gone, duplicated, out of order, or stuck behind another one. Usually nobody notices until a customer does.

---

## 1. Ignoring the result of `KafkaTemplate.send()`

```java
kafkaTemplate.send("orders", order.customerId(), event);   // return value dropped
return ResponseEntity.created(...).build();
```

**What goes wrong:** `send()` only puts the record in a buffer. If the broker later refuses it (record over `message.max.bytes`, too few in-sync replicas, broker unreachable past the timeout), the returned future fails *after* `send()` has returned. The caller replies 201, the event never exists, and the only trace is one ERROR line from `LoggingProducerListener` (topic 03's `FireAndForgetTest`).

**Fix:** `send(...).get(10, SECONDS)` when the caller must know, or `whenComplete((result, ex) -> ...)` that really handles `ex`. When the event must match a database commit, use the outbox (module 13 topic 19).

---

## 2. `acks=1` "for performance"

```yaml
spring.kafka.producer.acks: 1
```

**What goes wrong:** a write only the leader has acknowledged is lost if the leader dies before a follower copies it. Setting `acks=1` also silently turns the idempotent producer off, so the producer's own retries can write duplicates (topic 03's `AcksAndIdempotenceTest`).

**Fix:** keep the default `acks=all`, with replication factor 3 and `min.insync.replicas=2` on the topic. Tune `linger.ms`, `batch.size`, and compression for throughput instead.

---

## 3. A new consumer group on the default `auto.offset.reset=latest`

```java
@KafkaListener(topics = "orders", groupId = "loyalty-points")   // new service, nothing configured
```

**What goes wrong:** a group with no committed offset starts at the *end* of the log. Every record published before the service first started is skipped, with no error (topic 04's `OffsetResetTest`). The same default makes integration tests flaky: a message sent before the test's consumer is assigned its partition is never read (topic 13's `BrokenTestPatternsTest`).

**Fix:** `spring.kafka.consumer.auto-offset-reset: earliest`, unless old records are useless to this consumer. In tests, also wait for partition assignment before sending.

---

## 4. More consumers than partitions

```java
@KafkaListener(topics = "orders", groupId = "email", concurrency = "6")   // orders has 2 partitions
```

**What goes wrong:** within a group, each partition goes to exactly one consumer. Four of the six consumers never receive anything, and scaling out pods has the same non-effect: throughput stays flat (topic 04's `ConcurrencyAndRebalanceTest`, topic 12's `KafkaReplayAndScalingTest`).

**Fix:** create topics with enough partitions for the parallelism you'll need, and keep instances × concurrency at or below the partition count. Remember that adding partitions later moves keys to different partitions (mistake 5).

---

## 5. Sending events with no key because ordering "worked in testing"

```java
kafka.send("order-events", "order-1:SHIPPED");   // no key
```

**What goes wrong:** with no key, the producer's sticky partitioner keeps a small burst on one partition, so every test passes. Under load it switches partition each batch, and in topic 05 about two thirds of orders had their events split across partitions (`OutOfOrderTest`). Different consumers read different partitions at different speeds, and a shipped order shows as "created". Adding partitions later breaks ordering too: going from 3 to 6 partitions moved 507 of 1,000 keys (`PartitionKeyTest`).

**Fix:** key every event by the entity whose events must stay in order: `kafka.send("order-events", orderId, event)`. Choose the partition count up front, generously, and if you must increase it, drain the topic first or accept a reordering window.

---

## 6. Retrying a failing record forever, or not at all

```java
new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
```

**What goes wrong:** a poison record never succeeds, so its partition stops. Every record behind it waits and lag grows until someone deploys a fix (topic 07's `InfiniteRetryTest`). With nothing configured, the opposite happens: `FixedBackOff(0, 9)`, 10 instant attempts, then the record is skipped with one log line. That's silent data loss (`DefaultErrorHandlerTest`).

**Fix:** a few attempts with a back-off, then `DeadLetterPublishingRecoverer`, which publishes to `<topic>-dlt` in spring-kafka 3.3 (older articles say `.DLT`). Mark validation errors not retryable with `addNotRetryableExceptions(...)`. Alert on the dead-letter topic and replay after the fix (`DeadLetterTopicTest`).

---

## 7. `JsonDeserializer` without `ErrorHandlingDeserializer`

```yaml
spring.kafka.consumer.value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

**What goes wrong:** one malformed record fails inside `poll()`, before any listener or error handler sees it. The consumer fails on the same offset in a tight loop, about 1,600 times in under 0.2 s in the demo, with two ERROR lines and a stack trace each time. The partition is blocked and the logs fill the disk (topic 07's `DeserializationErrorBrokenTest`).

**Fix:** wrap it:

```yaml
spring.kafka.consumer.value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

The bad record then reaches the error handler and goes to the dead-letter topic on the first try (`DeserializationErrorTest`).

---

## 8. Checking for a duplicate event without a unique constraint

```java
if (jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class, id) > 0) return;
jdbc.update("INSERT INTO processed_event (event_id) VALUES (?)", id);
jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", amount, account);
```

**What goes wrong:** after a rebalance, two consumers can process the same record at once. Both `SELECT`s see 0, both credit the account, and one payment of 100 becomes a balance of 200 (topic 08's `ConcurrentDuplicateTest`). `@Transactional` doesn't help: neither transaction sees the other's uncommitted insert.

**Fix:** a primary key on `event_id`. Insert first, treat `DuplicateKeyException` as "already done", and keep that insert and the business change in one transaction (topic 08's `AccountService.creditOnce`).

---

## 9. Consumers that follow Spring Kafka's `__TypeId__` header

```java
JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
deserializer.addTrustedPackages("*");
```

**What goes wrong:** `JsonSerializer` puts the producer's full class name in a `__TypeId__` header, and this consumer uses it. When order-service moves `OrderPlaced` to another package, every record fails on every consumer with `ClassNotFoundException`, though no data changed (topic 09's `TypeHeaderTest`).

**Fix:** read into the consumer's own class: `spring.json.use.type.headers: false` with `spring.json.value.default.type`. Or agree on logical type names with `spring.json.type.mapping` on both sides.

---

## 10. Renaming a field in an event

```java
public record OrderPlaced(String orderId, long totalCents) { }   // was amountCents
```

**What goes wrong:** old consumers find no `amountCents`, so their `long` is `0`, and `totalCents` is ignored as unknown. No exception, no log line: every order ships with an amount of zero (topic 09's `FieldEvolutionTest`).

**Fix:** add the new field, keep the old one until every consumer has moved, then remove it. For bigger changes, publish a new event type. A schema registry with compatibility checks catches this before the deploy.

---

## 11. Treating a RabbitMQ publish (or its confirm) as "delivered"

```java
rabbit.convertAndSend("orders", "invoice.created", event, correlation);
correlation.getFuture().get().isAck();   // true
```

**What goes wrong:** an exchange routes only to queues whose bindings match. A message that matches none is accepted, acked, and dropped. A publisher confirm says only that the broker took it (topic 10's `UnroutableTest`, topic 11's `PublisherConfirmsTest`).

**Fix:** publish with `mandatory` (`spring.rabbitmq.template.mandatory: true`, `spring.rabbitmq.publisher-returns: true`) and check `correlation.getReturned()`, or give the exchange an alternate exchange that collects unroutable messages.

---

## 12. A `@RabbitListener` that throws, with no dead-letter exchange

```java
@RabbitListener(queues = "shipping.orders")
public void onOrderPlaced(OrderPlaced order) { validate(order); ... }   // throws on bad data
```

**What goes wrong:** by default a listener exception rejects the message *with requeue*, and the broker redelivers it at once. One bad message failed 100 times in about 0.2 s, and on a classic queue it never stops. A RabbitMQ 4 quorum queue gives up after 20 redeliveries (topic 11's `PoisonMessageTest`).

**Fix:** give the queue a dead-letter exchange (`QueueBuilder.durable("shipping.orders").deadLetterExchange("orders.dlx")`) bound to a DLQ, then set `spring.rabbitmq.listener.simple.default-requeue-rejected: false` or throw `AmqpRejectAndDontRequeueException` for permanent failures. For temporary failures, enable `spring.rabbitmq.listener.simple.retry` with a few attempts first (`RetryThenDeadLetterTest`). Watch the DLQ.

---

## 13. Choosing a queue, then needing history

```java
rabbit.convertAndSend("orders", "", event);   // analytics will subscribe next quarter
```

**What goes wrong:** RabbitMQ delivers only to queues bound when the message is published, and deletes each message once it's acknowledged. A consumer added later sees nothing that came before (topic 12's `RabbitReplayAndScalingTest`).

**Fix:** if new readers or replays are likely, use a log (Kafka, or RabbitMQ Streams), or store the events as well.

---

## 14. A messaging test that passes because nothing ran

```java
kafka.send("order", "order-x:-5");                       // typo: nobody listens on "order"
assertThat(shipping.shipmentFor("order-x")).isEmpty();   // green
```

**What goes wrong:** a test that asserts something did *not* happen passes when nothing happened at all: wrong topic, listener not started, a deserializer failing (topic 13's `KafkaOrderFlowTest`). Its cousin, `Thread.sleep(2000)` before asserting, is slow on fast machines and flaky on slow ones.

**Fix:** first wait for proof that the listener processed the message (`await().atMost(...).until(() -> listener.received() > before)`), then assert the absence with `await().during(...)`. Never use fixed sleeps.
